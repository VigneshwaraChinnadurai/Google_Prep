from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable
import importlib.util
import json


@dataclass
class StepResult:
    name: str
    details: str


class GraphMemory:
    """Simple graph memory for strategic relationships."""

    def __init__(self) -> None:
        self._edges: list[tuple[str, str, str]] = []

    def add_edge(self, source: str, relation: str, target: str) -> None:
        self._edges.append((source, relation, target))

    def query(self, source: str | None = None, relation: str | None = None) -> list[tuple[str, str, str]]:
        return [
            (s, r, t)
            for (s, r, t) in self._edges
            if (source is None or s == source) and (relation is None or r == relation)
        ]


class NewsAndDataAgent:
    def __init__(self, base_path: Path) -> None:
        self.base_path = base_path

    def fetch_quarterly_metrics(self) -> dict:
        with (self.base_path / "sample_inputs" / "quarterly_cloud_metrics.json").open("r", encoding="utf-8") as f:
            return json.load(f)

    def fetch_qualitative_signals(self) -> dict:
        with (self.base_path / "sample_inputs" / "qualitative_signals.json").open("r", encoding="utf-8") as f:
            return json.load(f)


class FinancialModelerAgent:
    def __init__(self, base_path: Path) -> None:
        self.base_path = base_path

    def create_market_share_tool(self) -> Callable[[dict, dict], dict]:
        """
        Dynamic tool creation.
        Writes a Python tool and returns a callable loaded at runtime.
        """
        tool_path = self.base_path / "generated_tools.py"
        tool_source = '''def calculate_market_share_delta(previous_revenue, current_revenue):
    """Return market share delta percentage points for each provider."""
    prev_total = sum(previous_revenue.values())
    curr_total = sum(current_revenue.values())

    if prev_total <= 0 or curr_total <= 0:
        raise ValueError("Total revenue must be positive for both periods")

    deltas = {}
    for key in current_revenue:
        prev_share = (previous_revenue[key] / prev_total) * 100.0
        curr_share = (current_revenue[key] / curr_total) * 100.0
        deltas[key] = round(curr_share - prev_share, 3)
    return deltas
'''
        tool_path.write_text(tool_source, encoding="utf-8")

        spec = importlib.util.spec_from_file_location("generated_tools", tool_path)
        if spec is None or spec.loader is None:
            raise RuntimeError("Failed to load generated tool spec")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.calculate_market_share_delta


class CritiqueModule:
    def critique(self, initial_conclusion: str) -> str:
        if "growing market share faster" in initial_conclusion.lower():
            return (
                "This insight is only a WHAT. Add causal analysis: whether growth is AI-product pull, "
                "price competition, enterprise bundling, or migration dynamics."
            )
        return "Conclusion depth is acceptable."


class AnalystAgent:
    def __init__(
        self,
        memory: GraphMemory,
        news_agent: NewsAndDataAgent,
        finance_agent: FinancialModelerAgent,
        critique_module: CritiqueModule,
    ) -> None:
        self.memory = memory
        self.news_agent = news_agent
        self.finance_agent = finance_agent
        self.critique_module = critique_module

    def build_plan(self) -> list[str]:
        return [
            "Acquire latest earnings data for GOOGL, MSFT, AMZN.",
            "Isolate cloud metrics: revenue, growth, operating income/margin.",
            "Benchmark cloud players quarter-over-quarter.",
            "Identify growth drivers from qualitative signals.",
            "Compute market share deltas with generated tool.",
            "Perform threat analysis focused on Azure exposure.",
            "Generate three actionable mitigation strategies for Microsoft.",
        ]

    def run(self) -> dict:
        plan = self.build_plan()
        metrics = self.news_agent.fetch_quarterly_metrics()
        signals = self.news_agent.fetch_qualitative_signals()

        tool = self.finance_agent.create_market_share_tool()

        companies = metrics["companies"]
        previous = {
            "Google": companies["GOOGL"]["previous"]["revenue_billion"],
            "Azure": companies["MSFT"]["previous"]["revenue_billion"],
            "AWS": companies["AMZN"]["previous"]["revenue_billion"],
        }
        current = {
            "Google": companies["GOOGL"]["current"]["revenue_billion"],
            "Azure": companies["MSFT"]["current"]["revenue_billion"],
            "AWS": companies["AMZN"]["current"]["revenue_billion"],
        }
        share_delta = tool(previous, current)

        initial_conclusion = (
            "Google is growing market share faster than Azure in the current quarter."
            if share_delta["Google"] > share_delta["Azure"]
            else "Azure is matching or exceeding Google share gains."
        )

        critique = self.critique_module.critique(initial_conclusion)

        memory_edges = self.memory.query(source="Microsoft Azure")
        memory_note = (
            "; ".join([f"{s} --[{r}]--> {t}" for s, r, t in memory_edges])
            if memory_edges
            else "No relevant memory links found."
        )

        threat_statement = (
            "Google Cloud's AI-native momentum threatens Azure's leadership in next-generation "
            "AI workloads, especially among data-native startups and teams prioritizing rapid "
            "model-to-production iteration."
        )

        strategies = [
            {
                "name": "Deepen the Office 365 + Copilot Moat",
                "actions": [
                    "Launch Copilot Advanced Deployment bundles for top enterprise accounts.",
                    "Provide dedicated Azure infrastructure + fine-tuning support on private data.",
                    "Tie migration credits to multi-year Microsoft 365 + Azure contracts.",
                ],
                "cost": "Medium-High (sales engineering, incentives, dedicated support)",
                "expected_outcome": "Higher enterprise AI retention and slower competitive displacement.",
            },
            {
                "name": "Launch a Multi-Cloud AI Control Plane",
                "actions": [
                    "Build open connectors for GCS/S3 data with first-class Azure ML orchestration.",
                    "Offer policy/governance templates for cross-cloud model lifecycle management.",
                    "Position Azure as the neutral enterprise AI operating layer.",
                ],
                "cost": "Medium (platform engineering + ecosystem partnerships)",
                "expected_outcome": "Reduces data-gravity disadvantage and expands Azure attach rate.",
            },
            {
                "name": "Acquire and Integrate Developer-Loved MLOps",
                "actions": [
                    "Target an MLOps platform with strong community adoption and simple UX.",
                    "Integrate natively into Azure ML and GitHub developer flows.",
                    "Bundle with enterprise governance features to combine ease + compliance.",
                ],
                "cost": "High (M&A + integration execution)",
                "expected_outcome": "Improves developer sentiment and shortens AI deployment cycles on Azure.",
            },
        ]

        return {
            "plan": plan,
            "metrics": metrics,
            "signals": signals,
            "share_delta": share_delta,
            "initial_conclusion": initial_conclusion,
            "critique": critique,
            "memory_note": memory_note,
            "threat_statement": threat_statement,
            "strategies": strategies,
        }
