from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd
from dimod import BinaryQuadraticModel
from dwave.samplers import SimulatedAnnealingSampler

from phase1_classical_portfolio import annualized_metrics, download_prices


TRADING_DAYS = 252
OUTPUT_DIR = Path("outputs")


def build_qubo(
    mean_returns: pd.Series,
    covariance_matrix: pd.DataFrame,
    target_assets: int,
    lambda_risk: float,
    lambda_return: float,
    penalty_strength: float,
) -> BinaryQuadraticModel:
    tickers = list(mean_returns.index)
    linear: dict[str, float] = {}
    quadratic: dict[tuple[str, str], float] = {}

    for i, ti in enumerate(tickers):
        # Diagonal risk term and linear return/cardinality terms.
        risk_diag = lambda_risk * float(covariance_matrix.iloc[i, i])
        ret_term = -lambda_return * float(mean_returns.iloc[i])
        cardinality_linear = penalty_strength * (1 - 2 * target_assets)
        linear[ti] = risk_diag + ret_term + cardinality_linear

    for i in range(len(tickers)):
        for j in range(i + 1, len(tickers)):
            ti = tickers[i]
            tj = tickers[j]
            risk_pair = 2.0 * lambda_risk * float(covariance_matrix.iloc[i, j])
            cardinality_pair = 2.0 * penalty_strength
            quadratic[(ti, tj)] = risk_pair + cardinality_pair

    offset = penalty_strength * (target_assets**2)
    return BinaryQuadraticModel(linear, quadratic, offset, vartype="BINARY")


def solve_simulator(bqm: BinaryQuadraticModel, num_reads: int = 4000):
    sampler = SimulatedAnnealingSampler()
    return sampler.sample(bqm, num_reads=num_reads)


def try_solve_qpu(bqm: BinaryQuadraticModel, num_reads: int = 200):
    # Imported lazily so local simulation works even without cloud credentials.
    from dwave.system import DWaveSampler, EmbeddingComposite

    sampler = EmbeddingComposite(DWaveSampler())
    return sampler.sample(bqm, num_reads=num_reads)


def decode_solution(sample: dict[str, int], mean_returns: pd.Series, covariance_matrix: pd.DataFrame):
    selected = [ticker for ticker, bit in sample.items() if bit == 1]
    if not selected:
        return {
            "selected": [],
            "weights": {},
            "expected_return": 0.0,
            "volatility": 0.0,
            "sharpe_like": 0.0,
        }

    # Simple mapping from binary selection to portfolio weights.
    weight = 1.0 / len(selected)
    weights = {ticker: weight for ticker in selected}

    w_vec = np.array([weights.get(t, 0.0) for t in mean_returns.index], dtype=float)
    exp_return = float(np.dot(w_vec, mean_returns.values))
    vol = float(np.sqrt(w_vec.T @ covariance_matrix.values @ w_vec))
    sharpe_like = exp_return / vol if vol > 0 else 0.0

    return {
        "selected": selected,
        "weights": weights,
        "expected_return": exp_return,
        "volatility": vol,
        "sharpe_like": sharpe_like,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Phase 2: QUBO portfolio selection")
    parser.add_argument("--tickers", nargs="+", default=["GOOGL", "AAPL", "MSFT", "AMZN"], help="Ticker symbols")
    parser.add_argument("--years", type=int, default=5, help="Years of price history")
    parser.add_argument("--k", type=int, default=2, help="Number of assets to select")
    parser.add_argument("--lambda-risk", type=float, default=1.0, help="Risk penalty coefficient")
    parser.add_argument("--lambda-return", type=float, default=1.0, help="Return reward coefficient")
    parser.add_argument("--penalty", type=float, default=5.0, help="Cardinality penalty coefficient")
    parser.add_argument("--use-qpu", action="store_true", help="Run on real D-Wave QPU (requires Leap credentials)")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    prices = download_prices(args.tickers, args.years)
    returns = prices.pct_change().dropna()
    mean_returns, covariance_matrix = annualized_metrics(returns)

    bqm = build_qubo(
        mean_returns=mean_returns,
        covariance_matrix=covariance_matrix,
        target_assets=args.k,
        lambda_risk=args.lambda_risk,
        lambda_return=args.lambda_return,
        penalty_strength=args.penalty,
    )

    if args.use_qpu:
        try:
            sampleset = try_solve_qpu(bqm)
            run_mode = "qpu"
        except Exception as exc:
            print(f"QPU run failed ({exc}). Falling back to local simulator.")
            sampleset = solve_simulator(bqm)
            run_mode = "simulator-fallback"
    else:
        sampleset = solve_simulator(bqm)
        run_mode = "simulator"

    best = sampleset.first
    best_sample = {k: int(v) for k, v in best.sample.items()}
    decoded = decode_solution(best_sample, mean_returns, covariance_matrix)

    out_df = pd.DataFrame(
        {
            "ticker": list(mean_returns.index),
            "selected": [best_sample[t] for t in mean_returns.index],
            "weight": [decoded["weights"].get(t, 0.0) for t in mean_returns.index],
            "annualized_return": [float(mean_returns[t]) for t in mean_returns.index],
        }
    )
    out_df.to_csv(OUTPUT_DIR / "qubo_solution.csv", index=False)

    print("=== QUBO Portfolio Selection ===")
    print(f"Mode:            {run_mode}")
    print(f"Selected assets: {decoded['selected']}")
    print(f"Expected return: {decoded['expected_return']:.4f}")
    print(f"Volatility:      {decoded['volatility']:.4f}")
    print(f"Sharpe-like:     {decoded['sharpe_like']:.4f}")
    print("Weights:")
    for ticker in mean_returns.index:
        print(f"  {ticker}: {decoded['weights'].get(ticker, 0.0) * 100:.2f}%")
    print("Output written: ./outputs/qubo_solution.csv")


if __name__ == "__main__":
    main()
