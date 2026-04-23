"""
Central config loader. Reads config/settings.yaml + .env, and builds the
Strands model provider based on the configured LLM.

Keep this the ONLY place that knows which LLM backend is in use — everything
else just asks for `build_model()`.
"""
from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml
from dotenv import load_dotenv

# Load .env once on import
load_dotenv()

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CONFIG_DIR = PROJECT_ROOT / "config"
DATA_DIR = PROJECT_ROOT / "data"


@lru_cache(maxsize=1)
def load_settings() -> dict[str, Any]:
    """Load settings.yaml with env-var overrides for a few common keys."""
    with open(CONFIG_DIR / "settings.yaml", "r", encoding="utf-8") as f:
        settings = yaml.safe_load(f)

    # Env overrides win
    if os.getenv("LLM_PROVIDER"):
        settings["llm"]["provider"] = os.getenv("LLM_PROVIDER")
    if os.getenv("LLM_MODEL_ID"):
        settings["llm"]["model_id"] = os.getenv("LLM_MODEL_ID")
    if os.getenv("MATCH_THRESHOLD"):
        settings["matching"]["threshold"] = float(os.getenv("MATCH_THRESHOLD"))
    if os.getenv("MAX_JOBS_PER_COMPANY"):
        settings["matching"]["max_jobs_per_company"] = int(
            os.getenv("MAX_JOBS_PER_COMPANY")
        )

    return settings


@lru_cache(maxsize=1)
def load_companies() -> list[dict[str, Any]]:
    with open(CONFIG_DIR / "companies.yaml", "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    return data.get("companies", [])


def get_applicant_profile() -> dict[str, str]:
    """Defaults used by the Applicator agent when filling forms."""
    return {
        "first_name": os.getenv("APPLICANT_FIRST_NAME", ""),
        "last_name": os.getenv("APPLICANT_LAST_NAME", ""),
        "email": os.getenv("APPLICANT_EMAIL", ""),
        "phone": os.getenv("APPLICANT_PHONE", ""),
        "location": os.getenv("APPLICANT_LOCATION", "Bangalore, India"),
        "linkedin": os.getenv("APPLICANT_LINKEDIN", ""),
        "github": os.getenv("APPLICANT_GITHUB", ""),
        "portfolio": os.getenv("APPLICANT_PORTFOLIO", ""),
        "resume_path": os.getenv("RESUME_PATH", "data/resume.pdf"),
    }


def build_model():
    """
    Construct the Strands model provider from settings.

    This is the only place in the codebase that touches provider-specific
    imports. Swap providers here and everything else keeps working.
    """
    settings = load_settings()
    provider = settings["llm"]["provider"].lower()
    model_id = settings["llm"]["model_id"]
    temperature = settings["llm"].get("temperature", 0.2)
    max_tokens = settings["llm"].get("max_output_tokens", 2048)

    if provider == "gemini":
        from strands.models.gemini import GeminiModel

        api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
        if not api_key:
            raise RuntimeError(
                "GEMINI_API_KEY not set. Get a free key at "
                "https://aistudio.google.com/apikey and put it in .env"
            )
        return GeminiModel(
            client_args={"api_key": api_key},
            model_id=model_id,
            params={
                "temperature": temperature,
                "max_output_tokens": max_tokens,
            },
        )

    if provider == "anthropic":
        from strands.models.anthropic import AnthropicModel

        api_key = os.getenv("ANTHROPIC_API_KEY")
        if not api_key:
            raise RuntimeError("ANTHROPIC_API_KEY not set in .env")
        return AnthropicModel(
            client_args={"api_key": api_key},
            model_id=model_id,
            params={"temperature": temperature, "max_tokens": max_tokens},
        )

    if provider == "openai":
        from strands.models.openai import OpenAIModel

        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("OPENAI_API_KEY not set in .env")
        return OpenAIModel(
            client_args={"api_key": api_key},
            model_id=model_id,
            params={"temperature": temperature, "max_tokens": max_tokens},
        )

    if provider == "bedrock":
        from strands.models import BedrockModel

        return BedrockModel(
            model_id=model_id, temperature=temperature, streaming=True
        )

    raise ValueError(f"Unknown LLM provider: {provider!r}")
