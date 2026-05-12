"""YAML-driven configuration loader."""
from __future__ import annotations
import os
from pathlib import Path
from typing import Any
import yaml
from pydantic import BaseModel, Field
from dotenv import load_dotenv

load_dotenv()

CONFIG_DIR = Path(__file__).resolve().parent.parent / "config"


class LLMConfig(BaseModel):
    provider: str = "gemini"
    model: str = "gemini-2.5-flash"
    temperature: float = 0.2
    api_key_env: str = "GEMINI_API_KEY"

    @property
    def api_key(self) -> str:
        key = os.environ.get(self.api_key_env, "")
        if not key:
            raise RuntimeError(f"Missing {self.api_key_env} in environment")
        return key


class ScrapingConfig(BaseModel):
    max_concurrent: int = 5
    cache_ttl_hours: int = 6
    user_agent: str = "JobFinder/0.1"
    respect_robots_txt: bool = True
    request_timeout_seconds: int = 30


class MatchingWeights(BaseModel):
    skills: float = 0.5
    experience: float = 0.3
    seniority: float = 0.2


class MatchingConfig(BaseModel):
    max_concurrent: int = 8
    weights: MatchingWeights = Field(default_factory=MatchingWeights)


class ApiConfig(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000
    cors_origins: list[str] = Field(default_factory=lambda: ["*"])


class SchedulerConfig(BaseModel):
    enabled: bool = True
    run_at: list[str] = Field(default_factory=lambda: ["00:00"])


class Settings(BaseModel):
    llm: LLMConfig = Field(default_factory=LLMConfig)
    scraping: ScrapingConfig = Field(default_factory=ScrapingConfig)
    matching: MatchingConfig = Field(default_factory=MatchingConfig)
    api: ApiConfig = Field(default_factory=ApiConfig)
    scheduler: SchedulerConfig = Field(default_factory=SchedulerConfig)


class Company(BaseModel):
    name: str
    ats: str  # greenhouse | lever | workday | generic
    slug: str | None = None
    url: str | None = None
    location_filter: str | None = None


def load_settings(path: Path | None = None) -> Settings:
    p = path or (CONFIG_DIR / "settings.yaml")
    data: dict[str, Any] = yaml.safe_load(p.read_text()) or {}
    return Settings.model_validate(data)


def load_companies(path: Path | None = None) -> list[Company]:
    p = path or (CONFIG_DIR / "companies.yaml")
    data = yaml.safe_load(p.read_text()) or []
    return [Company.model_validate(c) for c in data]
