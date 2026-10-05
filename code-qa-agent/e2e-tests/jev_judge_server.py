"""Eval judge sidecar backed by TypeSafe Jev (System One).

Drop-in replacement for the embedding /containment judge, same contract:
  POST /containment  — judge a reply against golden claims
  GET  /health

Env:
  TYPESAFE_API_KEY     required
  TYPESAFE_MODEL       default jev-1.13.0
  JUDGE_THRESHOLD      default 0.6
  JUDGE_RECORD_ONLY    default false; when true always returns ratio=1.0 (calibration)
  JUDGE_MAX_STATE_CHARS default 60000
"""

import logging
import os

from fastapi import FastAPI
from pydantic import BaseModel
from typesafe_sdk import Noul, RetryPolicy, TypeSafeClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("jev-judge")

MODEL = os.environ.get("TYPESAFE_MODEL", "jev-1.13.0")
THRESHOLD = float(os.environ.get("JUDGE_THRESHOLD", "0.6"))
RECORD_ONLY = os.environ.get("JUDGE_RECORD_ONLY", "false").lower() == "true"
MAX_STATE_CHARS = int(os.environ.get("JUDGE_MAX_STATE_CHARS", "60000"))

_client: TypeSafeClient | None = None


def _client_singleton() -> TypeSafeClient:
    global _client
    if _client is None:
        _client = TypeSafeClient(
            api_key=os.environ.get("TYPESAFE_API_KEY"),
            model=MODEL,
            retry=RetryPolicy(max_retries=3, backoff_initial=0.5, backoff_max=5.0),
            timeout=60.0,
        )
    return _client


def _instruction(claim: str) -> str:
    return f"该回答是否表达了以下含义（不要求用词一致）：{claim}"


class ContainmentRequest(BaseModel):
    claims: list[str]
    reply: str


class ContainmentResponse(BaseModel):
    scores: list[float]
    ratio: float
    threshold: float
    passed: bool


app = FastAPI()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL, "record_only": RECORD_ONLY}


@app.post("/containment", response_model=ContainmentResponse)
def containment(req: ContainmentRequest) -> ContainmentResponse:
    state = req.reply
    if len(state) > MAX_STATE_CHARS:
        log.warning("state truncated from %d to %d chars", len(state), MAX_STATE_CHARS)
        state = state[:MAX_STATE_CHARS]

    questions = {
        f"claim_{i}": Noul(instructions=_instruction(claim))
        for i, claim in enumerate(req.claims)
    }
    response = _client_singleton().system_one(state=state, questions=questions)
    scores = [
        round(float(response.answers[f"claim_{i}"].noul), 4)
        for i in range(len(req.claims))
    ]
    log.info("judged model=%s scores=%s", response.model, scores)

    if RECORD_ONLY:
        return ContainmentResponse(scores=scores, ratio=1.0, threshold=THRESHOLD, passed=True)

    ratio = min(scores) if scores else 0.0
    return ContainmentResponse(
        scores=scores, ratio=ratio, threshold=THRESHOLD, passed=ratio >= THRESHOLD
    )
