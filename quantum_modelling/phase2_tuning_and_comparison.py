from __future__ import annotations

import argparse
from itertools import product
from pathlib import Path

import pandas as pd

from phase1_classical_portfolio import (
    annualized_metrics,
    download_prices,
    optimize_max_sharpe,
)
from phase2_qubo_portfolio import build_qubo, decode_solution, solve_simulator


OUTPUT_DIR = Path("outputs")


def parse_float_list(text: str) -> list[float]:
    values = [v.strip() for v in text.split(",") if v.strip()]
    if not values:
        raise ValueError("At least one value is required.")
    return [float(v) for v in values]


def run_sweep(
    mean_returns: pd.Series,
    covariance_matrix: pd.DataFrame,
    k: int,
    lambda_risk_values: list[float],
    lambda_return_values: list[float],
    penalty_values: list[float],
    num_reads: int,
) -> pd.DataFrame:
    rows: list[dict[str, object]] = []

    for lambda_risk, lambda_return, penalty in product(
        lambda_risk_values,
        lambda_return_values,
        penalty_values,
    ):
        bqm = build_qubo(
            mean_returns=mean_returns,
            covariance_matrix=covariance_matrix,
            target_assets=k,
            lambda_risk=lambda_risk,
            lambda_return=lambda_return,
            penalty_strength=penalty,
        )
        sampleset = solve_simulator(bqm, num_reads=num_reads)
        best_sample = {name: int(bit) for name, bit in sampleset.first.sample.items()}
        decoded = decode_solution(best_sample, mean_returns, covariance_matrix)

        rows.append(
            {
                "k": k,
                "lambda_risk": lambda_risk,
                "lambda_return": lambda_return,
                "penalty": penalty,
                "selected_count": len(decoded["selected"]),
                "selected_assets": ",".join(decoded["selected"]),
                "expected_return": decoded["expected_return"],
                "volatility": decoded["volatility"],
                "sharpe_like": decoded["sharpe_like"],
                "energy": float(sampleset.first.energy),
            }
        )

    result = pd.DataFrame(rows)
    result = result.sort_values(by=["sharpe_like", "expected_return"], ascending=[False, False]).reset_index(drop=True)
    return result


def classical_vs_qubo_summary(
    mean_returns: pd.Series,
    covariance_matrix: pd.DataFrame,
    top_qubo_row: pd.Series,
    risk_free_rate: float,
) -> pd.DataFrame:
    classical = optimize_max_sharpe(mean_returns, covariance_matrix, risk_free_rate)

    classical_row = {
        "method": "classical_max_sharpe",
        "expected_return": classical.expected_return,
        "volatility": classical.volatility,
        "sharpe_metric": classical.sharpe_ratio,
        "selected_assets": ",".join(mean_returns.index[classical.weights > 1e-8]),
    }

    qubo_row = {
        "method": "qubo_best_from_sweep",
        "expected_return": float(top_qubo_row["expected_return"]),
        "volatility": float(top_qubo_row["volatility"]),
        "sharpe_metric": float(top_qubo_row["sharpe_like"]),
        "selected_assets": str(top_qubo_row["selected_assets"]),
    }

    return pd.DataFrame([classical_row, qubo_row])


def main() -> None:
    parser = argparse.ArgumentParser(description="Phase 2 parameter sweep + classical comparison")
    parser.add_argument("--tickers", nargs="+", default=["GOOGL", "AAPL", "MSFT", "AMZN"], help="Ticker symbols")
    parser.add_argument("--years", type=int, default=5, help="Years of price history")
    parser.add_argument("--k", type=int, default=2, help="Cardinality target for QUBO selection")
    parser.add_argument("--lambda-risk-grid", default="0.5,1.0,2.0", help="Comma-separated risk coefficient grid")
    parser.add_argument("--lambda-return-grid", default="0.5,1.0,2.0", help="Comma-separated return coefficient grid")
    parser.add_argument("--penalty-grid", default="3.0,5.0,8.0", help="Comma-separated penalty coefficient grid")
    parser.add_argument("--num-reads", type=int, default=3000, help="Simulator reads per setting")
    parser.add_argument("--risk-free-rate", type=float, default=0.02, help="Risk-free rate for classical Sharpe")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    prices = download_prices(args.tickers, args.years)
    returns = prices.pct_change().dropna()
    mean_returns, covariance_matrix = annualized_metrics(returns)

    lambda_risk_values = parse_float_list(args.lambda_risk_grid)
    lambda_return_values = parse_float_list(args.lambda_return_grid)
    penalty_values = parse_float_list(args.penalty_grid)

    sweep_df = run_sweep(
        mean_returns=mean_returns,
        covariance_matrix=covariance_matrix,
        k=args.k,
        lambda_risk_values=lambda_risk_values,
        lambda_return_values=lambda_return_values,
        penalty_values=penalty_values,
        num_reads=args.num_reads,
    )
    sweep_path = OUTPUT_DIR / "qubo_sweep_results.csv"
    sweep_df.to_csv(sweep_path, index=False)

    top_row = sweep_df.iloc[0]
    comparison_df = classical_vs_qubo_summary(
        mean_returns=mean_returns,
        covariance_matrix=covariance_matrix,
        top_qubo_row=top_row,
        risk_free_rate=args.risk_free_rate,
    )
    comparison_path = OUTPUT_DIR / "classical_vs_qubo.csv"
    comparison_df.to_csv(comparison_path, index=False)

    print("=== QUBO Sweep Complete ===")
    print(f"Grid size: {len(lambda_risk_values) * len(lambda_return_values) * len(penalty_values)}")
    print("Best QUBO setting:")
    print(
        f"  lambda_risk={top_row['lambda_risk']}, lambda_return={top_row['lambda_return']}, penalty={top_row['penalty']}"
    )
    print(
        f"  selected={top_row['selected_assets']}, return={top_row['expected_return']:.4f}, vol={top_row['volatility']:.4f}, sharpe_like={top_row['sharpe_like']:.4f}"
    )
    print(f"Sweep output: {sweep_path.as_posix()}")
    print(f"Comparison output: {comparison_path.as_posix()}")


if __name__ == "__main__":
    main()
