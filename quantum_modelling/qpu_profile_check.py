from __future__ import annotations

from pathlib import Path


OUTPUT_DIR = Path("quantum_modelling/outputs")


def main() -> None:
    print("=== D-Wave QPU Readiness Check ===")
    report_lines: list[str] = []

    try:
        from dwave.cloud import Client  # type: ignore[reportMissingImports]
    except Exception as exc:
        report_lines.append(f"ocean_import_error={exc}")
        report_lines.append("action=install_or_reinstall_dwave_ocean_sdk")
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        out_path = OUTPUT_DIR / "qpu_readiness.txt"
        out_path.write_text("\n".join(report_lines) + "\n", encoding="utf-8")
        print(f"Ocean client import failed: {exc}")
        print(f"Report written: {out_path.as_posix()}")
        return

    try:
        config = Client.from_config()
    except Exception as exc:
        report_lines.append(f"config_error={exc}")
        report_lines.append("action=run_dwave_setup")
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        out_path = OUTPUT_DIR / "qpu_readiness.txt"
        out_path.write_text("\n".join(report_lines) + "\n", encoding="utf-8")
        print(f"Unable to load D-Wave config: {exc}")
        print("Run: dwave setup")
        print(f"Report written: {out_path.as_posix()}")
        return

    api_endpoint = getattr(config.config, "endpoint", "unknown")
    profile = getattr(config.config, "profile", None) or "default"
    token_present = bool(getattr(config.config, "token", None))

    report_lines = [
        "QPU configuration status",
        f"profile={profile}",
        f"endpoint={api_endpoint}",
        f"token_present={token_present}",
    ]

    if not token_present:
        report_lines.append("token missing; run dwave setup to configure Leap credentials")

    try:
        with config as client:
            solvers = client.get_solvers(qpu=True)
            report_lines.append(f"available_qpu_solvers={len(solvers)}")
            if solvers:
                names = ",".join(sorted(s.name for s in solvers)[:5])
                report_lines.append(f"sample_solvers={names}")
    except Exception as exc:
        report_lines.append(f"solver_query_error={exc}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = OUTPUT_DIR / "qpu_readiness.txt"
    out_path.write_text("\n".join(report_lines) + "\n", encoding="utf-8")

    for line in report_lines:
        print(line)
    print(f"Report written: {out_path.as_posix()}")


if __name__ == "__main__":
    main()
