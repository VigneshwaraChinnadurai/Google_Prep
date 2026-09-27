# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal monorepo, not a single application: Vigneshwara's Google interview-prep tracker plus a collection of independent AI/ML, agentic-AI, and Android side projects built while preparing. There is no repo-wide build, lint, or test command — each subproject below is self-contained with its own dependencies and tooling. `cd` into the relevant subproject before running anything.

## Automated commits — expect drift in `git log`/`git status`

Two kinds of commits land on `main` without a human or Claude session driving them:

- **`Add LeetCode QA revision for <date>`** — pushed directly by the `mobile_apps/leetcode_checker` Android app via the GitHub Contents API (`RevisionExportManager.pushToGitHub`) every time its daily LeetCode revision auto-fetches. This is what populates `Leetcode_QA_Revision/<date>/` (`question.txt`, `answer.py`, `explanation.txt`) — don't hand-edit those folders, and don't be surprised to see several of these commits appear between sessions.
- **`chore: token write-permission probe`** — pushed by the same app's GitHub-token test in Global Settings (writes then relies on the next commit to overwrite/clean up).

Before pushing, always `git fetch` and check `git log HEAD..origin/main` for these first — they're not merge conflicts, just routine background activity from the phone. `prep_pathway/progress_state.json` also shows as locally modified independent of anything you did (see below); stash it (`git stash push -u -- prep_pathway/progress_state.json`) before rebasing, then pop it back after.

## `prep_pathway/` — the active prep tracker

`Google_Roadmap_v2.md` is the live 185-day study plan; `progress_log.md` and `progress_state.json` are its state files. These are maintained day-by-day by a separate personal Claude Code skill (`google-prep`, installed at `~/.claude/skills/google-prep`, outside this repo) — expect `progress_state.json` in particular to be touched by that skill's own runs independent of anything you're asked to do here. Don't restructure `Google_Roadmap_v2.md`'s format without checking how that skill parses it.

## Subprojects

**Actively maintained:**

- **`mobile_apps/leetcode_checker/`** — the Android app this session's work concentrates on. Has its own detailed `CLAUDE.md` in that directory (build/signing, ADB gotchas, architecture conventions) — read that instead of duplicating it here.
- **`Job_Finder_using_strands/`** — Python/Streamlit agentic job-application assistant built on AWS Strands Agents SDK + Gemini. Own venv: `python -m venv .venv && pip install -r requirements.txt && playwright install chromium`; smoke test via `pytest tests/`; run with `streamlit run src/ui/app.py`.
- **`mobile_apps/agentic_job_finder_mobile_latest/`** — client-server sibling of the job finder (FastAPI backend + Android client over LAN); has its own `pyproject.toml` and a `tests/test_smoke.py`.
- **`mobile_apps/job_finder_standalone_latest/`** — standalone on-device successor of the same idea (no backend, Gemini key entered directly in-app); separate Gradle project (`gradlew assembleDebug`).
- **`agentic_ai/usecase_3_autonomous_strategic_analysis/`** and **`agentic_ai/usecase_4_strategic_chatbot/`** — standalone Python agentic-AI research projects (own `requirements.txt`, `main.py`, `README.md`, architecture diagrams). **Cross-project coupling to know about**: `leetcode_checker/app/build.gradle.kts` reads `agentic_ai/usecase_4_strategic_chatbot/.env`'s `GEMINI_API_KEY` directly at Android build time for the app's in-app Strategic Chatbot feature — changing or removing that `.env` breaks that Android feature's build.
- **`applyfast/`** — a pnpm workspace (TypeScript), originally scaffolded on Replit (`.replit`, `replit.md`). Must install with `pnpm` specifically (the `preinstall` script hard-fails on npm/yarn). `pnpm run build` / `pnpm run typecheck`.

**Superseded / earlier iterations** (kept for reference, not where new work should go unless asked):

- `mobile_apps/leedcode_checker_ollama/` — predates `leetcode_checker`'s own built-in Ollama tab; that functionality now lives inside `leetcode_checker` directly.
- `mobile_apps/job_automation_agent_old/` and `mobile_apps/job_automation_standalone_old/` — earlier job-automation Android apps, explicitly named `_old`, replaced by the `_latest` pair above.

**Standalone research/learning projects** (each with its own `requirements.txt`/`README.md`, run as plain scripts — no shared tooling):

- `quantum_modelling/` — QUBO portfolio optimization vs. classical max-Sharpe, optionally against D-Wave Leap's QPU (`phase1_classical_portfolio.py`, `phase2_qubo_portfolio.py`). Also published standalone as the public `Quantum_Computing` GitHub repo.
- `peft_finetuning/` — PEFT fine-tuning package, runnable via `python -m peft_finetuning` (`__main__.py`) or `run.py`.
- `airllm_local_inference/` — local LLM inference tooling (`inference.py`, `chat.py`, `benchmark.py`).

**Reference material, not code:**

- `ML_Interview_Prep/` — numbered ML interview-prep markdown docs (traditional ML, CV, NLP, forecasting, RecSys, RAG/GenAI, agentic AI, quantization).
- `Leetcode_additional_efforts/` — extra LeetCode practice as Jupyter notebooks.
- `self_learning_documentations/` — deep-dive writeups of how the mobile apps and agentic-AI usecases were built.
- `Resume/` — `resume.tex` plus drafted rewrites of the public GitHub profile README and LinkedIn copy; these are staging drafts, not the live published versions.
- `Leetcode_QA_Revision/` — generated output only (see automated commits above).

A handful of root-level files (`DSA.txt`, `_deep_analysis_test.txt`, `_e2e_output.md`, `_gradio_caps.txt`, `job_automation.db`, `extract_resume.py`) predate the current subprojects and aren't tied to any of them.
