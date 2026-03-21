from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import yfinance as yf
from scipy.optimize import minimize


TICKERS = ["GOOGL", "AAPL", "MSFT", "AMZN"]
YEARS_OF_HISTORY = 5
RISK_FREE_RATE = 0.02
TRADING_DAYS = 252
N_FRONTIER_POINTS = 40
OUTPUT_DIR = Path("outputs")


@dataclass
class PortfolioResult:
    weights: np.ndarray
    expected_return: float
    volatility: float
    sharpe_ratio: float


def download_prices(tickers: Iterable[str], years: int) -> pd.DataFrame:
    end = pd.Timestamp.today().normalize()
    start = end - pd.DateOffset(years=years)

    series_by_ticker: dict[str, pd.Series] = {}
    failures: list[str] = []

    for ticker in tickers:
        last_error: Exception | None = None
        for _ in range(3):
            try:
                data = yf.download(
                    ticker,
                    start=start,
                    end=end,
                    auto_adjust=True,
                    progress=False,
                    threads=False,
                )
                if data is None or data.empty:
                    raise RuntimeError("No rows returned.")

                if "Close" not in data.columns:
                    raise RuntimeError("Missing Close column.")

                close_data = data["Close"]
                if isinstance(close_data, pd.DataFrame):
                    close_series = close_data.iloc[:, 0].rename(ticker)
                else:
                    close_series = close_data.rename(ticker)
                series_by_ticker[ticker] = close_series
                last_error = None
                break
            except Exception as err:
                last_error = err

        if last_error is not None:
            failures.append(f"{ticker}: {last_error}")

    if not series_by_ticker:
        detail = "; ".join(failures) if failures else "unknown download error"
        raise RuntimeError(f"No data downloaded from yfinance. Details: {detail}")

    prices = pd.concat(series_by_ticker.values(), axis=1)
    prices = prices.dropna(how="all").ffill().dropna()
    if prices.empty:
        raise RuntimeError("Price series is empty after cleaning.")

    missing_tickers = [t for t in tickers if t not in prices.columns]
    if missing_tickers:
        raise RuntimeError(f"Missing data for tickers: {missing_tickers}")

    return prices[list(tickers)]


def annualized_metrics(returns: pd.DataFrame) -> tuple[pd.Series, pd.DataFrame]:
    mean_returns = returns.mean() * TRADING_DAYS
    covariance_matrix = returns.cov() * TRADING_DAYS
    return mean_returns, covariance_matrix


def portfolio_performance(weights: np.ndarray, mean_returns: pd.Series, covariance_matrix: pd.DataFrame) -> tuple[float, float]:
    expected_return = float(np.dot(weights, mean_returns.values))
    volatility = float(np.sqrt(weights.T @ covariance_matrix.values @ weights))
    return expected_return, volatility


def negative_sharpe(weights: np.ndarray, mean_returns: pd.Series, covariance_matrix: pd.DataFrame, risk_free_rate: float) -> float:
    expected_return, volatility = portfolio_performance(weights, mean_returns, covariance_matrix)
    if volatility == 0:
        return np.inf
    sharpe = (expected_return - risk_free_rate) / volatility
    return -sharpe


def optimize_max_sharpe(mean_returns: pd.Series, covariance_matrix: pd.DataFrame, risk_free_rate: float) -> PortfolioResult:
    n_assets = len(mean_returns)
    init_weights = np.full(n_assets, 1.0 / n_assets)
    bounds = tuple((0.0, 1.0) for _ in range(n_assets))
    constraints = ({"type": "eq", "fun": lambda w: np.sum(w) - 1.0},)

    result = minimize(
        negative_sharpe,
        init_weights,
        args=(mean_returns, covariance_matrix, risk_free_rate),
        method="SLSQP",
        bounds=bounds,
        constraints=constraints,
    )

    if not result.success:
        raise RuntimeError(f"Max-Sharpe optimization failed: {result.message}")

    weights = result.x
    expected_return, volatility = portfolio_performance(weights, mean_returns, covariance_matrix)
    sharpe_ratio = (expected_return - risk_free_rate) / volatility
    return PortfolioResult(weights=weights, expected_return=expected_return, volatility=volatility, sharpe_ratio=sharpe_ratio)


def minimize_volatility_for_target_return(
    mean_returns: pd.Series,
    covariance_matrix: pd.DataFrame,
    target_return: float,
) -> PortfolioResult | None:
    n_assets = len(mean_returns)
    init_weights = np.full(n_assets, 1.0 / n_assets)
    bounds = tuple((0.0, 1.0) for _ in range(n_assets))

    constraints = (
        {"type": "eq", "fun": lambda w: np.sum(w) - 1.0},
        {"type": "eq", "fun": lambda w: np.dot(w, mean_returns.values) - target_return},
    )

    objective = lambda w: np.sqrt(w.T @ covariance_matrix.values @ w)
    result = minimize(
        objective,
        init_weights,
        method="SLSQP",
        bounds=bounds,
        constraints=constraints,
    )

    if not result.success:
        return None

    weights = result.x
    expected_return, volatility = portfolio_performance(weights, mean_returns, covariance_matrix)
    sharpe_ratio = (expected_return - RISK_FREE_RATE) / volatility if volatility > 0 else np.nan
    return PortfolioResult(weights=weights, expected_return=expected_return, volatility=volatility, sharpe_ratio=sharpe_ratio)


def optimize_min_volatility(mean_returns: pd.Series, covariance_matrix: pd.DataFrame) -> PortfolioResult:
    n_assets = len(mean_returns)
    init_weights = np.full(n_assets, 1.0 / n_assets)
    bounds = tuple((0.0, 1.0) for _ in range(n_assets))
    constraints = ({"type": "eq", "fun": lambda w: np.sum(w) - 1.0},)

    objective = lambda w: np.sqrt(w.T @ covariance_matrix.values @ w)
    result = minimize(objective, init_weights, method="SLSQP", bounds=bounds, constraints=constraints)

    if not result.success:
        raise RuntimeError(f"Min-volatility optimization failed: {result.message}")

    weights = result.x
    expected_return, volatility = portfolio_performance(weights, mean_returns, covariance_matrix)
    sharpe_ratio = (expected_return - RISK_FREE_RATE) / volatility if volatility > 0 else np.nan
    return PortfolioResult(weights=weights, expected_return=expected_return, volatility=volatility, sharpe_ratio=sharpe_ratio)


def build_efficient_frontier(mean_returns: pd.Series, covariance_matrix: pd.DataFrame, n_points: int) -> pd.DataFrame:
    min_ret = float(mean_returns.min())
    max_ret = float(mean_returns.max())
    target_returns = np.linspace(min_ret, max_ret, n_points)

    rows = []
    for target in target_returns:
        result = minimize_volatility_for_target_return(mean_returns, covariance_matrix, target)
        if result is not None:
            rows.append(
                {
                    "target_return": target,
                    "expected_return": result.expected_return,
                    "volatility": result.volatility,
                    "sharpe_ratio": result.sharpe_ratio,
                }
            )

    return pd.DataFrame(rows)


def plot_frontier(frontier_df: pd.DataFrame, max_sharpe: PortfolioResult, min_vol: PortfolioResult, output_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(10, 6))

    ax.plot(frontier_df["volatility"], frontier_df["expected_return"], lw=2, label="Efficient Frontier")
    ax.scatter(max_sharpe.volatility, max_sharpe.expected_return, marker="*", s=250, label="Max Sharpe")
    ax.scatter(min_vol.volatility, min_vol.expected_return, marker="o", s=120, label="Min Volatility")

    ax.set_title("Efficient Frontier (Long-only)")
    ax.set_xlabel("Annualized Volatility")
    ax.set_ylabel("Annualized Expected Return")
    ax.grid(alpha=0.25)
    ax.legend()

    fig.tight_layout()
    fig.savefig(output_path, dpi=150)
    plt.close(fig)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    prices = download_prices(TICKERS, YEARS_OF_HISTORY)
    returns = prices.pct_change().dropna()
    mean_returns, covariance_matrix = annualized_metrics(returns)

    mean_returns.to_csv(OUTPUT_DIR / "mean_returns.csv", header=["annualized_mean_return"])
    covariance_matrix.to_csv(OUTPUT_DIR / "covariance_matrix.csv")

    max_sharpe = optimize_max_sharpe(mean_returns, covariance_matrix, RISK_FREE_RATE)
    min_vol = optimize_min_volatility(mean_returns, covariance_matrix)
    frontier_df = build_efficient_frontier(mean_returns, covariance_matrix, N_FRONTIER_POINTS)
    frontier_df.to_csv(OUTPUT_DIR / "frontier_points.csv", index=False)

    plot_frontier(frontier_df, max_sharpe, min_vol, OUTPUT_DIR / "efficient_frontier.png")

    weights = pd.Series(max_sharpe.weights, index=mean_returns.index, name="weight")

    print("=== Max Sharpe Portfolio ===")
    print(f"Expected return: {max_sharpe.expected_return:.4f}")
    print(f"Volatility:      {max_sharpe.volatility:.4f}")
    print(f"Sharpe ratio:    {max_sharpe.sharpe_ratio:.4f}")
    print("Weights:")
    print((weights * 100).round(2).astype(str) + "%")

    print("\nOutputs written to ./outputs")


if __name__ == "__main__":
    main()
