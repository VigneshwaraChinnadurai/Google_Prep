# Quantum Modelling - Classical and Quantum Portfolio Optimization

This folder contains both the Phase 1 classical baseline and a Phase 2 QUBO-based quantum workflow.

## Phase 1: Classical baseline

- Script: `phase1_classical_portfolio.py`
- Downloads adjusted close prices for selected tickers using yfinance
- Computes daily returns and covariance matrix
- Finds max-Sharpe portfolio weights with scipy
- Plots an efficient frontier and highlights max-Sharpe and min-volatility portfolios

## Setup

1. Install dependencies (already installed in your configured venv):

```powershell
pip install -r requirements.txt
```

2. Run Phase 1:

```powershell
python phase1_classical_portfolio.py
```

3. Phase 1 outputs:

- `outputs/mean_returns.csv`
- `outputs/covariance_matrix.csv`
- `outputs/frontier_points.csv`
- `outputs/efficient_frontier.png`

## Phase 2: QUBO formulation and solve

- Script: `phase2_qubo_portfolio.py`
- Converts portfolio selection into a QUBO with:
	- risk term from covariance matrix
	- return reward from mean returns
	- cardinality penalty to enforce selecting exactly `k` assets
- Solves first on a local simulated annealer (default)
- Can optionally run on a real D-Wave QPU

Run local simulator:

```powershell
python phase2_qubo_portfolio.py --k 2
```

Optional run on QPU (after configuring D-Wave credentials):

```powershell
python phase2_qubo_portfolio.py --k 2 --use-qpu
```

Phase 2 output:

- `outputs/qubo_solution.csv`

## Phase 2.5: Tuning and benchmark comparison

- Script: `phase2_tuning_and_comparison.py`
- Runs a coefficient sweep over:
	- `lambda_risk`
	- `lambda_return`
	- `penalty`
- Selects best QUBO setting by Sharpe-like score
- Builds a side-by-side metrics comparison against classical max-Sharpe

Run with defaults:

```powershell
python phase2_tuning_and_comparison.py --k 2
```

Outputs:

- `outputs/qubo_sweep_results.csv`
- `outputs/classical_vs_qubo.csv`

## Phase 2.6: QPU readiness diagnostics

- Script: `qpu_profile_check.py`
- Checks whether D-Wave credentials and QPU solvers are discoverable from your environment

Run:

```powershell
python qpu_profile_check.py
```

Output:

- `outputs/qpu_readiness.txt`

## D-Wave Leap setup (for real QPU)

1. Create a Leap account at https://cloud.dwavesys.com/leap/
2. Get your API token
3. Configure credentials:

```powershell
dwave setup
```

Once this is done, `--use-qpu` will submit the QUBO to cloud hardware.

## Notes

- Default tickers: GOOGL, AAPL, MSFT, AMZN
- Default data window: last 5 years
- You can modify constants at the top of the script for different universes and assumptions.
