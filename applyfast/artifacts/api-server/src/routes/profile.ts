import { Router, Request, Response } from "express";
import { db, profileTable } from "../db/index.js";
import { eq } from "drizzle-orm";
import multer from "multer";

const router = Router();
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 10 * 1024 * 1024 } });

router.get("/", async (_req: Request, res: Response) => {
  const profiles = await db.select().from(profileTable).limit(1);
  if (profiles.length === 0) {
    res.status(404).json({ error: "Profile not found" });
    return;
  }
  res.json(profiles[0]);
});

router.put("/", async (req: Request, res: Response) => {
  const data = req.body;
  const existing = await db.select().from(profileTable).limit(1);

  if (existing.length === 0) {
    const [profile] = await db.insert(profileTable).values({
      name: data.name,
      email: data.email,
      phone: data.phone || null,
      location: data.location || null,
      workAuthorization: data.workAuthorization || null,
      linkedinUrl: data.linkedinUrl || null,
      githubUrl: data.githubUrl || null,
      portfolioUrl: data.portfolioUrl || null,
      skills: data.skills || [],
      workHistory: data.workHistory || [],
      jobFilters: data.jobFilters || {},
      targetCompanies: data.targetCompanies || [],
    }).returning();
    res.json(profile);
  } else {
    const [profile] = await db.update(profileTable)
      .set({
        name: data.name,
        email: data.email,
        phone: data.phone || null,
        location: data.location || null,
        workAuthorization: data.workAuthorization || null,
        linkedinUrl: data.linkedinUrl || null,
        githubUrl: data.githubUrl || null,
        portfolioUrl: data.portfolioUrl || null,
        skills: data.skills || [],
        workHistory: data.workHistory || [],
        jobFilters: data.jobFilters || {},
        targetCompanies: data.targetCompanies || [],
        updatedAt: new Date().toISOString(),
      })
      .where(eq(profileTable.id, existing[0].id))
      .returning();
    res.json(profile);
  }
});

router.post("/resume", upload.single("file"), async (req: Request, res: Response) => {
  const file = req.file;
  if (!file) {
    res.status(400).json({ error: "No file uploaded" });
    return;
  }

  let text = "";
  const ext = file.originalname.toLowerCase();

  if (ext.endsWith(".pdf")) {
    const pdfParse = (await import("pdf-parse")).default;
    const result = await pdfParse(file.buffer);
    text = result.text;
  } else if (ext.endsWith(".docx")) {
    const mammoth = await import("mammoth");
    const result = await mammoth.extractRawText({ buffer: file.buffer });
    text = result.value;
  } else {
    res.status(400).json({ error: "Unsupported file format. Use PDF or DOCX." });
    return;
  }

  const existing = await db.select().from(profileTable).limit(1);
  if (existing.length === 0) {
    await db.insert(profileTable).values({
      name: "User",
      email: "user@example.com",
      resumeText: text,
      resumeFileName: file.originalname,
    });
  } else {
    await db.update(profileTable)
      .set({ resumeText: text, resumeFileName: file.originalname, updatedAt: new Date().toISOString() })
      .where(eq(profileTable.id, existing[0].id));
  }

  res.json({
    fileName: file.originalname,
    textLength: text.length,
    preview: text.slice(0, 500),
  });
});

export default router;
