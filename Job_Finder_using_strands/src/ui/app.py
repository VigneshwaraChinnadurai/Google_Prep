"""
Streamlit UI for the Job Finder.

Layout:
  Sidebar: run-pipeline controls (Parse Resume, Scrape, Match, Reload)
  Main:    a card per matched job with Approve / Skip buttons

Approve a card -> calls Orchestrator.apply_to(match), which opens the portal
in a visible browser and pre-fills the form. The user finishes in the browser.
"""
from __future__ import annotations

import sys
from pathlib import Path

# Make src/ importable when run via `streamlit run src/ui/app.py`
PROJECT_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT_ROOT))

import streamlit as st

from src.agents import Orchestrator
from src.config import get_applicant_profile, load_companies, load_settings
from src.models import ApplicationStatus

st.set_page_config(
    page_title="Job Finder — Agentic Applications",
    page_icon="🎯",
    layout="wide",
)


# ---------- state init ----------

if "orch" not in st.session_state:
    st.session_state.orch = Orchestrator()
if "app_status" not in st.session_state:
    # job_id -> ApplicationStatus
    st.session_state.app_status = {}
if "apply_results" not in st.session_state:
    # job_id -> result dict from the applicator
    st.session_state.apply_results = {}

orch: Orchestrator = st.session_state.orch


# ---------- sidebar ----------

with st.sidebar:
    st.title("🎯 Job Finder")
    st.caption("Agentic job applications powered by Strands")

    st.divider()
    st.subheader("1. Resume")

    profile = orch.load_cached_profile()
    applicant = get_applicant_profile()
    default_resume = applicant.get("resume_path", "data/resume.pdf")

    resume_path = st.text_input("Resume path", value=default_resume)
    use_cached_profile = st.checkbox("Use cached profile if available", value=True)

    if st.button("Parse resume", type="primary"):
        with st.spinner("Extracting profile..."):
            try:
                profile = orch.parse_resume(resume_path, use_cache=use_cached_profile)
                st.success(f"Parsed profile for **{profile.full_name}**")
            except Exception as e:
                st.error(f"Failed to parse resume: {e}")

    if profile:
        with st.expander("Profile summary", expanded=False):
            st.write(f"**Name:** {profile.full_name}")
            st.write(f"**Field:** {profile.field or '—'}")
            st.write(f"**Seniority:** {profile.seniority or '—'}")
            st.write(
                f"**Experience:** {profile.total_years_experience:.1f} years"
            )
            st.write(f"**Location:** {profile.location or '—'}")
            st.write(
                "**Top skills:** "
                + ", ".join(s.name for s in profile.skills[:12])
            )

    st.divider()
    st.subheader("2. Scrape companies")

    companies = load_companies()
    st.caption(f"{len(companies)} companies configured in companies.yaml")

    use_cached_jobs = st.checkbox("Use cached job list if available", value=False)

    if st.button("Scrape all companies"):
        with st.spinner("Scraping... this may take 30-60 seconds"):
            try:
                jobs = orch.scrape_all(use_cache=use_cached_jobs)
                st.success(f"Found {len(jobs)} jobs total")
            except Exception as e:
                st.error(f"Scraping failed: {e}")

    st.divider()
    st.subheader("3. Match")

    settings = load_settings()
    threshold = st.slider(
        "Match threshold",
        0.0,
        1.0,
        value=float(settings["matching"]["threshold"]),
        step=0.05,
        help="Jobs with a fit score below this are hidden.",
    )

    if st.button("Score + rank jobs"):
        if not orch._resume:
            st.error("Parse a resume first.")
        elif not orch._jobs:
            st.error("Scrape jobs first.")
        else:
            progress = st.progress(0.0)
            status_text = st.empty()

            def _on_progress(i, total, job):
                progress.progress(i / total)
                status_text.caption(f"Scoring {i}/{total}: {job.company} — {job.title}")

            matches = orch.match_all(
                on_progress=_on_progress, threshold=threshold
            )
            progress.empty()
            status_text.empty()
            st.success(f"{len(matches)} jobs at/above threshold {threshold:.2f}")

    st.divider()

    if st.button("Reload matches from cache"):
        st.rerun()


# ---------- main pane ----------

st.title("Matches awaiting your approval")

matches = orch.load_cached_matches()

if not matches:
    st.info(
        "No matches yet. Use the sidebar to parse your resume, scrape companies, "
        "and score jobs."
    )
    st.stop()


# Quick stats row
total = len(matches)
strong = sum(1 for m in matches if m.recommendation == "strong_match")
good = sum(1 for m in matches if m.recommendation == "good_match")
stretch = sum(1 for m in matches if m.recommendation == "stretch")

c1, c2, c3, c4 = st.columns(4)
c1.metric("Total matches", total)
c2.metric("Strong", strong)
c3.metric("Good", good)
c4.metric("Stretch", stretch)

# Filters row
f1, f2 = st.columns([1, 2])
with f1:
    rec_filter = st.multiselect(
        "Show recommendation",
        ["strong_match", "good_match", "stretch", "poor_match"],
        default=["strong_match", "good_match"],
    )
with f2:
    company_filter = st.multiselect(
        "Filter by company",
        sorted({m.job.company for m in matches}),
        default=[],
    )

visible = [
    m
    for m in matches
    if m.recommendation in rec_filter
    and (not company_filter or m.job.company in company_filter)
]

st.caption(f"Showing {len(visible)} of {total}")
st.divider()

# Cards
for match in visible:
    job_id = match.job.id
    status = st.session_state.app_status.get(job_id, ApplicationStatus.PENDING_REVIEW)

    with st.container(border=True):
        col_info, col_actions = st.columns([3, 1])

        with col_info:
            score_pct = int(match.fit_score * 100)
            badge_color = {
                "strong_match": "🟢",
                "good_match": "🔵",
                "stretch": "🟡",
                "poor_match": "🔴",
            }.get(match.recommendation, "⚪")

            st.markdown(
                f"### {badge_color} {match.job.title}  \n"
                f"**{match.job.company}** · {match.job.location or '—'}  ·  "
                f"Fit **{score_pct}%**"
            )
            st.caption(match.rationale)

            sub_a, sub_b = st.columns(2)
            with sub_a:
                if match.matched_skills:
                    st.write("**✅ Matched:** " + ", ".join(match.matched_skills[:10]))
            with sub_b:
                if match.missing_skills:
                    st.write("**⚠️ Missing:** " + ", ".join(match.missing_skills[:8]))

            with st.expander("Scores + JD"):
                score_cols = st.columns(4)
                score_cols[0].metric("Skills", f"{match.skill_match_score:.2f}")
                score_cols[1].metric("Experience", f"{match.experience_match_score:.2f}")
                score_cols[2].metric("Role", f"{match.role_alignment_score:.2f}")
                score_cols[3].metric("Location", f"{match.location_score:.2f}")
                st.link_button("Open posting ↗", match.job.url)
                if match.job.description:
                    st.text_area(
                        "JD",
                        value=match.job.description[:3000],
                        height=200,
                        disabled=True,
                        key=f"jd_{job_id}",
                    )

        with col_actions:
            if status == ApplicationStatus.PENDING_REVIEW:
                if st.button("✅ Approve & Apply", key=f"ok_{job_id}", type="primary"):
                    st.session_state.app_status[job_id] = ApplicationStatus.APPLYING
                    with st.spinner("Opening browser and pre-filling form..."):
                        try:
                            result = orch.apply_to(match)
                            st.session_state.apply_results[job_id] = result
                            st.session_state.app_status[job_id] = (
                                ApplicationStatus.PREFILLED_AWAITING_SUBMIT
                                if result.get("success")
                                else ApplicationStatus.FAILED
                            )
                        except Exception as e:
                            st.session_state.apply_results[job_id] = {
                                "success": False,
                                "error": str(e),
                            }
                            st.session_state.app_status[job_id] = (
                                ApplicationStatus.FAILED
                            )
                    st.rerun()

                if st.button("⏭️ Skip", key=f"skip_{job_id}"):
                    st.session_state.app_status[job_id] = ApplicationStatus.SKIPPED
                    st.rerun()

            elif status == ApplicationStatus.PREFILLED_AWAITING_SUBMIT:
                st.success("Form pre-filled")
                st.caption("Finish in the browser window, then click below.")
                if st.button("Mark as submitted", key=f"done_{job_id}"):
                    st.session_state.app_status[job_id] = ApplicationStatus.SUBMITTED
                    st.rerun()

            elif status == ApplicationStatus.SUBMITTED:
                st.success("✅ Submitted")

            elif status == ApplicationStatus.SKIPPED:
                st.info("Skipped")
                if st.button("Undo skip", key=f"undo_{job_id}"):
                    st.session_state.app_status[job_id] = ApplicationStatus.PENDING_REVIEW
                    st.rerun()

            elif status == ApplicationStatus.FAILED:
                st.error("Apply failed")
                if st.button("Retry", key=f"retry_{job_id}"):
                    st.session_state.app_status[job_id] = ApplicationStatus.PENDING_REVIEW
                    st.rerun()

        # Show apply-result details if present
        result = st.session_state.apply_results.get(job_id)
        if result and status in (
            ApplicationStatus.PREFILLED_AWAITING_SUBMIT,
            ApplicationStatus.FAILED,
        ):
            with st.expander("Prefill details"):
                if result.get("success"):
                    st.write("**Filled:**", result.get("filled_fields", []))
                    if result.get("skipped_fields"):
                        st.write("**Skipped:**", result["skipped_fields"])
                    st.caption(result.get("note", ""))
                else:
                    st.error(result.get("error", "unknown error"))
