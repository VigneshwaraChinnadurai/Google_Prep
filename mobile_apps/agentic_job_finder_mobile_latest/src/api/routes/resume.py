import pathlib
import tempfile
from fastapi import APIRouter, Depends, UploadFile, File, HTTPException
from src.api.deps import require_api_key
from src.agents.resume_parser import ResumeParserAgent
from src.orchestrator import Orchestrator
from src.schemas import Resume

router = APIRouter(dependencies=[Depends(require_api_key)])
_parser = ResumeParserAgent()
_orch = Orchestrator()


@router.post("/upload", response_model=Resume)
async def upload_resume(file: UploadFile = File(...)) -> Resume:
    if not file.filename or not file.filename.lower().endswith((".pdf", ".docx")):
        raise HTTPException(400, "Upload .pdf or .docx")
    suffix = pathlib.Path(file.filename).suffix
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name
    resume = await _parser.parse(tmp_path, persist=True)
    _orch.set_resume(resume)
    return resume


@router.get("", response_model=Resume | None)
async def get_resume() -> Resume | None:
    return _parser.load_cached()
