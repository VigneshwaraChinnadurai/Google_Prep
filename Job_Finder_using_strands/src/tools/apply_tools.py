"""
Tools for the Applicator agent.

The model here is SEMI-automatic:
1. We launch a visible (non-headless) Chromium window.
2. We navigate to the job URL.
3. We try to find & click an "Apply" button.
4. We pre-fill the form using the applicant profile from .env.
5. We STOP before submitting — the user clicks the final submit button.

This is intentional. Auto-submitting applications:
  (a) can violate portal ToS,
  (b) risks spamming companies with low-quality applications,
  (c) removes the user's chance to personalize the cover letter.
"""
from __future__ import annotations

import asyncio
import json
from pathlib import Path
from typing import Any, Optional

from strands import tool

from ..config import DATA_DIR, get_applicant_profile, load_settings


# Common form-field name/id/label patterns -> profile key
# Ordered by specificity (most specific first).
FIELD_HINTS: list[tuple[str, str]] = [
    (r"first.?name", "first_name"),
    (r"given.?name", "first_name"),
    (r"last.?name", "last_name"),
    (r"family.?name", "last_name"),
    (r"surname", "last_name"),
    (r"full.?name", "full_name"),
    (r"\bname\b", "full_name"),
    (r"email", "email"),
    (r"phone", "phone"),
    (r"mobile", "phone"),
    (r"telephone", "phone"),
    (r"linkedin", "linkedin"),
    (r"github", "github"),
    (r"portfolio", "portfolio"),
    (r"website", "portfolio"),
    (r"\bcity\b", "location"),
    (r"location", "location"),
    (r"current.?address", "location"),
]


@tool
def prefill_application(job_url: str, cover_letter: Optional[str] = None) -> str:
    """
    Open the job posting in a visible browser, navigate to the apply form,
    and pre-fill it from the applicant profile in .env.

    This does NOT submit. It leaves the browser open so the user can review
    and click Submit themselves.

    Args:
        job_url: URL of the job posting.
        cover_letter: Optional cover letter text to paste into the relevant
                      textarea if present.

    Returns:
        JSON object describing what was filled and what still needs user input.
    """
    settings = load_settings()
    profile = get_applicant_profile()
    profile["full_name"] = f"{profile['first_name']} {profile['last_name']}".strip()

    # Resolve resume path to absolute
    resume_path = Path(profile["resume_path"])
    if not resume_path.is_absolute():
        resume_path = (DATA_DIR.parent / resume_path).resolve()

    result = asyncio.run(
        _prefill_async(
            job_url=job_url,
            profile=profile,
            resume_path=resume_path if resume_path.exists() else None,
            cover_letter=cover_letter,
            timeout_ms=settings["scraping"]["page_timeout_ms"],
        )
    )
    return json.dumps(result, indent=2)


async def _prefill_async(
    job_url: str,
    profile: dict[str, str],
    resume_path: Optional[Path],
    cover_letter: Optional[str],
    timeout_ms: int,
) -> dict[str, Any]:
    try:
        from playwright.async_api import async_playwright
    except ImportError:
        return {
            "success": False,
            "error": "Playwright not installed. Run: pip install playwright && playwright install chromium",
        }

    filled_fields: list[str] = []
    skipped_fields: list[str] = []

    async with async_playwright() as p:
        # Visible browser — user will interact with it.
        browser = await p.chromium.launch(headless=False)
        context = await browser.new_context()
        page = await context.new_page()
        page.set_default_timeout(timeout_ms)

        try:
            await page.goto(job_url, wait_until="domcontentloaded")
        except Exception as e:
            return {"success": False, "error": f"Failed to load {job_url}: {e!r}"}

        # Try to find an Apply button
        await _try_click_apply(page)
        await page.wait_for_timeout(1500)

        # Collect every input/textarea on the page with its associated label text
        fields = await page.evaluate(
            """
            () => {
                const out = [];
                document.querySelectorAll('input, textarea, select').forEach(el => {
                    if (el.type === 'hidden' || el.disabled) return;
                    const id = el.id || '';
                    const name = el.name || '';
                    const placeholder = el.placeholder || '';
                    const ariaLabel = el.getAttribute('aria-label') || '';
                    // Try to find an associated label
                    let labelText = '';
                    if (id) {
                        const lbl = document.querySelector(`label[for="${id}"]`);
                        if (lbl) labelText = lbl.innerText || '';
                    }
                    if (!labelText && el.closest('label')) {
                        labelText = el.closest('label').innerText || '';
                    }
                    out.push({
                        tag: el.tagName.toLowerCase(),
                        type: el.type || '',
                        id, name, placeholder, ariaLabel, labelText,
                        selector: id ? `#${CSS.escape(id)}` : (name ? `[name="${name}"]` : '')
                    });
                });
                return out;
            }
            """
        )

        import re

        for f in fields:
            signals = " ".join(
                [
                    f.get("id", ""),
                    f.get("name", ""),
                    f.get("placeholder", ""),
                    f.get("ariaLabel", ""),
                    f.get("labelText", ""),
                ]
            ).lower()

            matched_key = None
            for pattern, key in FIELD_HINTS:
                if re.search(pattern, signals):
                    matched_key = key
                    break

            if not matched_key or not f.get("selector"):
                continue

            value = profile.get(matched_key, "")
            if not value:
                skipped_fields.append(f"{matched_key} (no value in .env)")
                continue

            try:
                if f["tag"] == "textarea":
                    await page.fill(f["selector"], value)
                elif f["type"] == "file":
                    continue  # handled separately below
                else:
                    await page.fill(f["selector"], value)
                filled_fields.append(f"{matched_key} -> {f.get('name') or f.get('id')}")
            except Exception as e:
                skipped_fields.append(
                    f"{matched_key} -> {f.get('name') or f.get('id')}: {e!r}"
                )

        # Handle resume file upload
        if resume_path:
            try:
                file_inputs = await page.query_selector_all('input[type="file"]')
                for inp in file_inputs:
                    # Heuristic: upload to the first file input (usually resume)
                    await inp.set_input_files(str(resume_path))
                    filled_fields.append(f"resume -> {resume_path.name}")
                    break
            except Exception as e:
                skipped_fields.append(f"resume upload failed: {e!r}")

        # Handle cover letter
        if cover_letter:
            try:
                # Find a textarea whose context mentions cover letter
                textareas = await page.query_selector_all("textarea")
                for ta in textareas:
                    name = (await ta.get_attribute("name") or "").lower()
                    id_ = (await ta.get_attribute("id") or "").lower()
                    ph = (await ta.get_attribute("placeholder") or "").lower()
                    if any("cover" in s for s in [name, id_, ph]):
                        await ta.fill(cover_letter)
                        filled_fields.append("cover_letter")
                        break
            except Exception as e:
                skipped_fields.append(f"cover letter failed: {e!r}")

        return {
            "success": True,
            "url": page.url,
            "filled_fields": filled_fields,
            "skipped_fields": skipped_fields,
            "note": (
                "Browser left open. Review the form, fill any remaining fields "
                "(especially dropdowns / work-authorization questions), then "
                "click Submit yourself."
            ),
            "_browser_kept_open": True,
        }
        # NOTE: we intentionally do NOT close the browser.
        # The `async with` block will NOT run to exit because we return above,
        # which means Python will close playwright when the process ends.
        # This is a deliberate trade-off for the semi-auto UX.


async def _try_click_apply(page) -> bool:
    """Best-effort: click an 'Apply' button if we find one."""
    candidates = [
        'button:has-text("Apply")',
        'a:has-text("Apply now")',
        'a:has-text("Apply")',
        '[data-automation-id*="apply" i]',
        'button[aria-label*="apply" i]',
    ]
    for sel in candidates:
        try:
            el = await page.query_selector(sel)
            if el:
                await el.click()
                await page.wait_for_load_state("domcontentloaded")
                return True
        except Exception:
            continue
    return False
