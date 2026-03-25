"""
Production-grade Gemini API client.

Capabilities
------------
* Text generation (plain and JSON-mode)
* Google-Search-grounded generation (fetches *real* web data)
* Embeddings via text-embedding-004
* Token counting (remote endpoint) and fast local estimation
* Transparent response caching with diskcache (content-addressed)
* Exponential-backoff retry on transient errors
* Cumulative token-usage accounting
* **CostGuard integration** — every call is budget-checked & logged
"""
from __future__ import annotations

import hashlib
import json
import logging
import time
from dataclasses import dataclass, field
from typing import Any

import diskcache
import requests as _requests

from config import GeminiConfig
from cost_guard import CostGuard

logger = logging.getLogger(__name__)


# ── Data containers ──────────────────────────────────────────────────────
@dataclass
class LLMResponse:
    text: str
    input_tokens: int = 0
    output_tokens: int = 0
    cached: bool = False
    model: str = ""
    grounding_sources: list[dict] = field(default_factory=list)


@dataclass
class EmbeddingResult:
    values: list[float]
    token_count: int = 0


# ── Client ───────────────────────────────────────────────────────────────
class GeminiClient:
    """Thin, caching, retry-aware wrapper around the Gemini REST API."""

    def __init__(
        self,
        config: GeminiConfig,
        cache_dir: str | None = None,
        cost_guard: CostGuard | None = None,
    ) -> None:
        self.cfg = config
        self.guard = cost_guard or CostGuard()
        self._s = _requests.Session()
        self._s.headers.update({"Content-Type": "application/json"})
        self._cache: diskcache.Cache | None = None
        if cache_dir:
            self._cache = diskcache.Cache(cache_dir, size_limit=500 * 1024 * 1024)
        self._tok_in = 0
        self._tok_out = 0
        self._cache_hits = 0

    # ── usage stats ──────────────────────────────────────────────────
    @property
    def usage(self) -> dict[str, int]:
        return {"input_tokens": self._tok_in, "output_tokens": self._tok_out,
                "cache_hits": self._cache_hits}

    # ── URL builder ──────────────────────────────────────────────────
    def _url(self, model: str, method: str) -> str:
        return f"{self.cfg.base_url}/models/{model}:{method}?key={self.cfg.api_key}"

    # ── cache key ────────────────────────────────────────────────────
    @staticmethod
    def _ckey(prefix: str, blob: str) -> str:
        return f"{prefix}:{hashlib.sha256(blob.encode()).hexdigest()}"

    # ── retry loop ───────────────────────────────────────────────────
    def _post(self, url: str, body: dict) -> dict:
        last_err: str = ""
        for attempt in range(self.cfg.max_retries):
            try:
                r = self._s.post(url, json=body, timeout=self.cfg.request_timeout)
                if r.status_code == 200:
                    return r.json()
                if r.status_code in (429, 500, 502, 503):
                    wait = self.cfg.retry_base_delay * (2 ** attempt)
                    logger.warning("Gemini %d – retry %d/%d in %.1fs",
                                   r.status_code, attempt + 1, self.cfg.max_retries, wait)
                    time.sleep(wait)
                    last_err = f"HTTP {r.status_code}: {r.text[:400]}"
                    continue
                raise RuntimeError(f"Gemini API {r.status_code}: {r.text[:800]}")
            except _requests.exceptions.Timeout:
                wait = self.cfg.retry_base_delay * (2 ** attempt)
                logger.warning("Timeout – retry in %.1fs", wait)
                time.sleep(wait)
                last_err = "timeout"
        raise RuntimeError(f"Gemini failed after {self.cfg.max_retries} retries – {last_err}")

    # ── token helpers ────────────────────────────────────────────────
    def count_tokens(self, text: str, model: str | None = None) -> int:
        url = self._url(model or self.cfg.generation_model, "countTokens")
        return self._post(url, {"contents": [{"parts": [{"text": text}]}]}).get("totalTokens", 0)

    @staticmethod
    def estimate_tokens(text: str) -> int:
        """~4 chars per token for English text."""
        return max(1, len(text) // 4)

    # ── generation ───────────────────────────────────────────────────
    def generate(
        self,
        prompt: str,
        *,
        system: str = "",
        temperature: float | None = None,
        max_tokens: int | None = None,
        json_mode: bool = False,
        use_cache: bool = True,
    ) -> LLMResponse:
        ck = self._ckey("gen", f"{system}|{prompt}|json={json_mode}")
        if use_cache and self._cache and ck in self._cache:
            self._cache_hits += 1
            self.guard.record_cache_hit()
            return LLMResponse(**self._cache[ck], cached=True)

        gen_cfg: dict[str, Any] = {
            "temperature": temperature if temperature is not None else self.cfg.temperature,
            "topP": self.cfg.top_p,
            "maxOutputTokens": max_tokens or self.cfg.max_output_tokens,
        }
        if json_mode:
            gen_cfg["responseMimeType"] = "application/json"

        body: dict[str, Any] = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": gen_cfg,
        }
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}

        # ── DRY RUN guard ────────────────────────────────────────
        if self.guard.dry_run:
            logger.info("[DRY RUN] generate() skipped — returning placeholder")
            return LLMResponse(text="[DRY RUN — LLM call skipped]",
                               model=self.cfg.generation_model)

        # ── budget check BEFORE call ────────────────────────────────
        self.guard.check()

        raw = self._post(self._url(self.cfg.generation_model, "generateContent"), body)
        text = self._extract_text(raw)
        usage = raw.get("usageMetadata", {})
        ti = usage.get("promptTokenCount", self.estimate_tokens(prompt))
        to = usage.get("candidatesTokenCount", self.estimate_tokens(text))
        self._tok_in += ti
        self._tok_out += to

        cost = self.guard.record(ti, to, self.cfg.generation_model)
        logger.info("generate: %d in / %d out  $%.5f  (total $%.4f)",
                    ti, to, cost, self.guard.total_cost_usd)

        resp = LLMResponse(text=text, input_tokens=ti, output_tokens=to,
                           model=self.cfg.generation_model)
        if use_cache and self._cache:
            self._cache[ck] = {"text": text, "input_tokens": ti, "output_tokens": to,
                               "model": self.cfg.generation_model}
        return resp

    # ── grounded generation (real web data via Google Search) ────────
    def generate_grounded(
        self,
        prompt: str,
        *,
        system: str = "",
        temperature: float | None = None,
        max_tokens: int | None = None,
        use_cache: bool = True,
    ) -> LLMResponse:
        """Call Gemini with Google Search grounding – returns *real* web-sourced data."""
        ck = self._ckey("gnd", f"{system}|{prompt}")
        if use_cache and self._cache and ck in self._cache:
            self._cache_hits += 1
            self.guard.record_cache_hit()
            d = self._cache[ck]
            return LLMResponse(**d, cached=True)

        gen_cfg: dict[str, Any] = {
            "temperature": temperature if temperature is not None else self.cfg.temperature,
            "topP": self.cfg.top_p,
            "maxOutputTokens": max_tokens or self.cfg.max_output_tokens,
        }
        body: dict[str, Any] = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": gen_cfg,
            "tools": [{"googleSearch": {}}],
        }
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}

        # ── DRY RUN guard ────────────────────────────────────────
        if self.guard.dry_run:
            logger.info("[DRY RUN] generate_grounded() skipped")
            return LLMResponse(text="[DRY RUN — grounded call skipped]",
                               model=self.cfg.generation_model)

        self.guard.check()

        raw = self._post(self._url(self.cfg.generation_model, "generateContent"), body)
        text = self._extract_text(raw)

        # Harvest grounding metadata (real URLs / snippets)
        sources: list[dict] = []
        gm = raw.get("candidates", [{}])[0].get("groundingMetadata", {})
        for chunk in gm.get("groundingChunks", []):
            web = chunk.get("web", {})
            if web:
                sources.append({"title": web.get("title", ""), "uri": web.get("uri", "")})

        usage = raw.get("usageMetadata", {})
        ti = usage.get("promptTokenCount", self.estimate_tokens(prompt))
        to = usage.get("candidatesTokenCount", self.estimate_tokens(text))
        self._tok_in += ti
        self._tok_out += to

        cost = self.guard.record(ti, to, self.cfg.generation_model)
        logger.info("grounded: %d in / %d out  $%.5f  (total $%.4f)",
                    ti, to, cost, self.guard.total_cost_usd)

        resp = LLMResponse(text=text, input_tokens=ti, output_tokens=to,
                           model=self.cfg.generation_model, grounding_sources=sources)
        if use_cache and self._cache:
            self._cache[ck] = {"text": text, "input_tokens": ti, "output_tokens": to,
                               "model": self.cfg.generation_model,
                               "grounding_sources": sources}
        return resp

    # ── embeddings ───────────────────────────────────────────────────
    def embed(self, text: str, task: str = "RETRIEVAL_DOCUMENT") -> EmbeddingResult:
        ck = self._ckey("emb", f"{task}:{text}")
        if self._cache and ck in self._cache:
            self._cache_hits += 1
            self.guard.record_cache_hit()
            return EmbeddingResult(**self._cache[ck])

        if self.guard.dry_run:
            return EmbeddingResult(values=[], token_count=0)

        body = {
            "model": f"models/{self.cfg.embedding_model}",
            "content": {"parts": [{"text": text}]},
            "taskType": task,
        }
        raw = self._post(self._url(self.cfg.embedding_model, "embedContent"), body)
        vals = raw.get("embedding", {}).get("values", [])
        tc = self.estimate_tokens(text)
        self._tok_in += tc
        self.guard.record_embedding(tc)   # free, but tracked
        er = EmbeddingResult(values=vals, token_count=tc)
        if self._cache:
            self._cache[ck] = {"values": vals, "token_count": tc}
        return er

    def embed_batch(self, texts: list[str], task: str = "RETRIEVAL_DOCUMENT") -> list[EmbeddingResult]:
        """Batch embed using batchEmbedContents — up to 100 per call.

        Falls back to sequential if batch fails. Adds rate-limit delay
        between batches to stay within free-tier limits.
        """
        # Check cache first for all
        results: list[EmbeddingResult | None] = [None] * len(texts)
        uncached: list[tuple[int, str]] = []
        for i, txt in enumerate(texts):
            ck = self._ckey("emb", f"{task}:{txt}")
            if self._cache and ck in self._cache:
                self._cache_hits += 1
                self.guard.record_cache_hit()
                results[i] = EmbeddingResult(**self._cache[ck])
            else:
                uncached.append((i, txt))

        if self.guard.dry_run:
            for i, _ in uncached:
                results[i] = EmbeddingResult(values=[], token_count=0)
            return results  # type: ignore

        # Batch API — up to 100 per call
        BATCH_SIZE = 100
        for batch_start in range(0, len(uncached), BATCH_SIZE):
            batch = uncached[batch_start:batch_start + BATCH_SIZE]
            requests_list = [
                {"model": f"models/{self.cfg.embedding_model}",
                 "content": {"parts": [{"text": txt}]},
                 "taskType": task}
                for _, txt in batch
            ]
            url = self._url(self.cfg.embedding_model, "batchEmbedContents")
            try:
                raw = self._post(url, {"requests": requests_list})
                embeddings = raw.get("embeddings", [])
                for j, (idx, txt) in enumerate(batch):
                    if j < len(embeddings):
                        vals = embeddings[j].get("values", [])
                    else:
                        vals = []
                    tc = self.estimate_tokens(txt)
                    self._tok_in += tc
                    self.guard.record_embedding(tc)
                    er = EmbeddingResult(values=vals, token_count=tc)
                    results[idx] = er
                    if self._cache:
                        ck = self._ckey("emb", f"{task}:{txt}")
                        self._cache[ck] = {"values": vals, "token_count": tc}
            except Exception as e:
                logger.warning("Batch embed failed (%s), falling back to sequential", e)
                for idx, txt in batch:
                    results[idx] = self.embed(txt, task)

            # Rate-limit delay between batches
            if batch_start + BATCH_SIZE < len(uncached):
                time.sleep(2.0)

        logger.info("embed_batch: %d total, %d cached, %d embedded",
                    len(texts), len(texts) - len(uncached), len(uncached))
        return results  # type: ignore

    # ── helpers ──────────────────────────────────────────────────────
    @staticmethod
    def _extract_text(raw: dict) -> str:
        try:
            parts = raw["candidates"][0]["content"]["parts"]
            return "\n".join(p.get("text", "") for p in parts).strip()
        except (KeyError, IndexError):
            logger.error("Unexpected Gemini response: %s", json.dumps(raw)[:500])
            return ""

    def close(self) -> None:
        self._s.close()
        if self._cache:
            self._cache.close()
