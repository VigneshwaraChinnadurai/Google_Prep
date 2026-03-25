from __future__ import annotations

from pathlib import Path
from datetime import datetime, timezone

from agents import AnalystAgent, CritiqueModule, FinancialModelerAgent, GraphMemory, NewsAndDataAgent


def render_report(result: dict) -> str:
    companies = result["metrics"]["companies"]
    quarter = result["metrics"]["quarter"]

    google = companies["GOOGL"]["current"]
    azure = companies["MSFT"]["current"]
    aws = companies["AMZN"]["current"]

    strategy_rows = []
    for idx, s in enumerate(result["strategies"], start=1):
        actions = "<br>".join([f"- {a}" for a in s["actions"]])
        strategy_rows.append(
            f"| {idx}. {s['name']} | {actions} | {s['cost']} | {s['expected_outcome']} |"
        )

    signals_text = "\n".join(
        [f"- [{item['type']}] {item['summary']}" for item in result["signals"]["sources"]]
    )

    plan_text = "\n".join([f"{i}. {step}" for i, step in enumerate(result["plan"], start=1)])

    report = f"""# Strategic Response to Google Cloud's AI-Led Growth

Generated: {datetime.now(timezone.utc).isoformat()}  
Analyzed quarter: {quarter}

## 1) Executive Summary
Alphabet's latest quarter indicates sustained Google Cloud acceleration, with stronger AI/workload pull than generic infrastructure expansion. The primary threat to Microsoft Azure is targeted erosion in next-generation AI workloads, especially where developer speed and integrated data+ML tooling drive platform choice.

## 2) Key Metrics Snapshot
- Google Cloud revenue: ${google['revenue_billion']}B (YoY {google['yoy_growth_percent']}%, QoQ {google['qoq_growth_percent']}%), operating margin {google['operating_margin_percent']}%.
- Microsoft Azure revenue (estimated cloud segment for benchmark): ${azure['revenue_billion']}B (YoY {azure['yoy_growth_percent']}%, QoQ {azure['qoq_growth_percent']}%), operating margin {azure['operating_margin_percent']}%.
- AWS revenue: ${aws['revenue_billion']}B (YoY {aws['yoy_growth_percent']}%, QoQ {aws['qoq_growth_percent']}%), operating margin {aws['operating_margin_percent']}%.

## 3) Market Share Delta (Sequential)
- Google: {result['share_delta']['Google']} percentage points
- Azure: {result['share_delta']['Azure']} percentage points
- AWS: {result['share_delta']['AWS']} percentage points

Initial conclusion: {result['initial_conclusion']}

Critique module feedback: {result['critique']}

## 4) Causal Signals (Why Google Is Gaining)
{signals_text}

## 5) Memory-Backed Strategic Context
{result['memory_note']}

## 6) Primary Strategic Threat to Azure
{result['threat_statement']}

## 7) Three Actionable Strategies for Microsoft
| Strategy | Actions | Potential Cost | Expected Outcome |
|---|---|---|---|
{chr(10).join(strategy_rows)}

## 8) Agentic Execution Trace
{plan_text}
"""
    return report


def main() -> None:
    base_path = Path(__file__).resolve().parent

    memory = GraphMemory()
    memory.add_edge("Microsoft Azure", "StronglyLinkedTo", "Office365/EnterpriseSales")
    memory.add_edge("Google Cloud", "StronglyLinkedTo", "AI-First Data-Native Workloads")

    news_agent = NewsAndDataAgent(base_path)
    finance_agent = FinancialModelerAgent(base_path)
    critique = CritiqueModule()

    analyst = AnalystAgent(
        memory=memory,
        news_agent=news_agent,
        finance_agent=finance_agent,
        critique_module=critique,
    )

    result = analyst.run()
    report = render_report(result)

    output_path = base_path / "outputs" / "strategic_report.md"
    output_path.write_text(report, encoding="utf-8")

    print("Analysis complete.")
    print(f"Report path: {output_path}")


if __name__ == "__main__":
    main()
