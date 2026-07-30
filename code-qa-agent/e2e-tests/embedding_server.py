"""Eval sidecar: embedding similarity + NLI entailment + semantic containment.

Endpoints:
  POST /similarity   — cosine similarity via sentence-transformers
  POST /entailment   — NLI entailment/contradiction via cross-encoder
  POST /containment  — semantic containment: cross-similarity of claims vs reply sentences

Runs on port 8001.
"""

import os
import re
from contextlib import asynccontextmanager
from typing import Any

import numpy as np
import torch
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity
from transformers import AutoModelForSequenceClassification, AutoTokenizer

EMBEDDING_MODEL = os.environ.get("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
NLI_MODEL = os.environ.get("NLI_MODEL", "MoritzLaurer/mDeBERTa-v3-base-mnli-xnli")
CONTAINMENT_THRESHOLD = float(os.environ.get("CONTAINMENT_THRESHOLD", "0.6"))

_embedding_model: SentenceTransformer | None = None
_nli_tokenizer: Any = None
_nli_model: Any = None
# id2label mapping for the NLI model (0=entailment, 1=neutral, 2=contradiction)
_nli_id2label: dict[int, str] = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _embedding_model, _nli_tokenizer, _nli_model, _nli_id2label
    _embedding_model = SentenceTransformer(EMBEDDING_MODEL)
    _nli_tokenizer = AutoTokenizer.from_pretrained(NLI_MODEL)
    _nli_model = AutoModelForSequenceClassification.from_pretrained(NLI_MODEL)
    _nli_model.eval()
    _nli_id2label = _nli_model.config.id2label if hasattr(_nli_model.config, "id2label") else {}
    yield


app = FastAPI(lifespan=lifespan)


class TextPairRequest(BaseModel):
    text1: str
    text2: str


# ── cosine similarity (kept for backward compatibility) ──

@app.post("/similarity")
def similarity(req: TextPairRequest) -> dict:
    embeds = _embedding_model.encode(
        [req.text1, req.text2],
        normalize_embeddings=True,
    )
    score = float(cosine_similarity([embeds[0]], [embeds[1]])[0][0])
    return {"cosine_similarity": round(score, 4)}


# ── NLI entailment detection ──

class EntailmentResponse(BaseModel):
    entailment: float
    neutral: float
    contradiction: float
    score: float  # entailment - contradiction, range [-1, 1]
    passed: bool


@app.post("/entailment", response_model=EntailmentResponse)
def entailment(req: TextPairRequest) -> EntailmentResponse:
    """Check if text2 (premise/golden) entails text1 (hypothesis/actual).

    text1 = actual agent output (hypothesis)
    text2 = golden standard response (premise)

    Returns:
        entailment:     prob that golden entails actual (higher = better)
        contradiction:  prob that golden contradicts actual (higher = worse)
        score:          entailment - contradiction  (range -1 to 1)
        passed:         entailment > contradiction
    """
    inputs = _nli_tokenizer(
        req.text2,  # premise = golden
        req.text1,  # hypothesis = actual
        return_tensors="pt",
        truncation=True,
        max_length=1024,
    )
    with torch.no_grad():
        logits = _nli_model(**inputs).logits
    probs = torch.softmax(logits, dim=1)[0].tolist()

    # Resolve label order by matching id2label
    scores_by_label: dict[str, float] = {}
    for i, p in enumerate(probs):
        label = _nli_id2label.get(i, f"LABEL_{i}").lower()
        # Normalize common label names
        for canonical in ("entailment", "neutral", "contradiction"):
            if canonical in label:
                scores_by_label[canonical] = p
                break
        else:
            scores_by_label.setdefault(label, p)

    ent = scores_by_label.get("entailment", probs[0] if len(probs) > 0 else 0.0)
    neu = scores_by_label.get("neutral", probs[1] if len(probs) > 1 else 0.0)
    con = scores_by_label.get("contradiction", probs[2] if len(probs) > 2 else 0.0)

    score = ent - con  # positive = consistent, negative = contradictory
    return EntailmentResponse(
        entailment=round(ent, 4),
        neutral=round(neu, 4),
        contradiction=round(con, 4),
        score=round(score, 4),
        passed=ent > con,
    )


@app.get("/health")
def health():
    return {
        "status": "ok",
        "embedding_model": EMBEDDING_MODEL,
        "nli_model": NLI_MODEL,
        "nli_id2label": _nli_id2label,
    }


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
