"""Competition agent — fans out across all registered platform adapters."""
from __future__ import annotations
import asyncio
import logging
from src.competitions import all_adapters
from src.schemas import Competition

logger = logging.getLogger(__name__)


class CompetitionAgent:
    """Aggregates hiring/coding competitions across platforms."""

    async def fetch_all(self) -> dict[str, list[Competition]]:
        """Fetch all platforms in parallel. Returns {platform: [competitions]}."""
        adapters = all_adapters()

        async def _run(name: str, adapter):
            try:
                return name, await adapter.fetch()
            except Exception:
                logger.exception("competition fetch failed for %s", name)
                return name, []

        tasks = [_run(n, a) for n, a in adapters.items()]
        results = await asyncio.gather(*tasks)
        return dict(results)
