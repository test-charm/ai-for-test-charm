"""Eval sidecar: semantic containment scoring.

Endpoints:
  POST /containment  — cross-similarity of claims vs reply sentences
  GET  /health       — model info

Runs on port 8001.
"""

import os
import re
from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity

EMBEDDING_MODEL = os.environ.get("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
CONTAINMENT_THRESHOLD = float(os.environ.get("CONTAINMENT_THRESHOLD", "0.6"))

_embedding_model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _embedding_model
    _embedding_model = SentenceTransformer(EMBEDDING_MODEL)
    yield


app = FastAPI(lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "embedding_model": EMBEDDING_MODEL}


# ── semantic containment ──


def _strip_markdown(text: str) -> str:
    """Strip markdown formatting, keeping code content."""
    text = re.sub(r'```[a-z]*\n?', ' ', text)
    text = re.sub(r'\n?```', ' ', text)
    text = re.sub(r'\|', ' ', text)
    text = re.sub(r'\*\*|__|`', '', text)
    text = re.sub(r'(?m)^[-*]{3,}\s*$', '', text)
    return text


def _split_sentences(text: str) -> list[str]:
    """Split text into sentences on Chinese/English punctuation."""
    text = _strip_markdown(text)
    text = re.sub(r'[ \t]+', ' ', text).strip()
    sentences = re.split(r'(?<=[。！？.!?\n])\s*', text)
    return [s.strip() for s in sentences if len(s.strip()) >= 10]


class ContainmentRequest(BaseModel):
    claims: list[str]
    reply: str


class ContainmentResponse(BaseModel):
    scores: list[float]
    ratio: float
    threshold: float
    passed: bool


@app.post("/containment", response_model=ContainmentResponse)
def containment(req: ContainmentRequest) -> ContainmentResponse:
    """Cross-similarity: for each claim, find max cosine similarity against any reply sentence."""
    reply_sentences = _split_sentences(req.reply)
    if not reply_sentences:
        return ContainmentResponse(
            scores=[0.0] * len(req.claims), ratio=0.0,
            threshold=CONTAINMENT_THRESHOLD, passed=False,
        )

    claim_embeds = _embedding_model.encode(req.claims, normalize_embeddings=True)
    reply_embeds = _embedding_model.encode(reply_sentences, normalize_embeddings=True)
    cos_scores = cosine_similarity(claim_embeds, reply_embeds)

    scores = [float(np.max(row)) for row in cos_scores]
    ratio = round(float(np.mean(scores)), 4) if scores else 0.0

    return ContainmentResponse(
        scores=[round(s, 4) for s in scores],
        ratio=ratio,
        threshold=CONTAINMENT_THRESHOLD,
        passed=ratio >= CONTAINMENT_THRESHOLD,
    )
