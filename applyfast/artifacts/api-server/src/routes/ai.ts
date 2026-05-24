import { Router, Request, Response } from "express";
import { db, tailoredContentTable, jobsTable, profileTable } from "../db/index.js";
import { eq } from "drizzle-orm";
import { GoogleGenAI } from "@google/genai";

const router = Router();

router.post("/tailor/:jobId", async (req: Request, res: Response) => {
  const jobId = Number(req.params.jobId);

  // Check cached
  const [cached] = await db.select().from(tailoredContentTable).where(eq(tailoredContentTable.jobId, jobId));
  if (cached) {
    res.json({ ...cached, cached: true });
    return;
  }

  // Get job
  const [job] = await db.select().from(jobsTable).where(eq(jobsTable.id, jobId));
  if (!job) {
    res.status(404).json({ error: "Job not found" });
    return;
  }

  // Get profile with resume
  const [profile] = await db.select().from(profileTable).limit(1);
  if (!profile?.resumeText) {
    res.status(400).json({ error: "No resume uploaded yet. Please upload your resume first." });
    return;
  }

  const apiKey = process.env.GOOGLE_API_KEY;
  if (!apiKey) {
    res.status(500).json({ error: "GOOGLE_API_KEY not configured" });
    return;
  }

  try {
    const ai = new GoogleGenAI({ apiKey });
    const prompt = `You are a career advisor AI. Given a candidate's resume and a job posting, generate tailored application content.

RESUME:
${profile.resumeText}

JOB POSTING:
Title: ${job.title}
Company: ${job.company}
Location: ${job.location || "Not specified"}
Description: ${job.description}

Generate the following in JSON format:
{
  "tailoredBullets": ["bullet1", "bullet2", "bullet3"],
  "coverLetter": "A 150-word cover letter tailored to this specific job",
  "matchScore": 0-100,
  "matchReasoning": "Brief explanation of the match score"
}

The 3 tailored bullets should highlight the candidate's most relevant experience for THIS specific role.
The cover letter should be concise, specific, and mention the company by name.
The match score should honestly reflect how well the candidate fits (consider skills, experience, and role requirements).`;

    const response = await ai.models.generateContent({
      model: "gemini-2.0-flash",
      contents: prompt,
      config: {
        responseMimeType: "application/json",
      },
    });

    const text = response.text || "{}";
    const result = JSON.parse(text);

    const [content] = await db.insert(tailoredContentTable).values({
      jobId,
      tailoredBullets: result.tailoredBullets || [],
      coverLetter: result.coverLetter || "",
      matchScore: result.matchScore || 0,
      matchReasoning: result.matchReasoning || "",
    }).returning();

    res.json({ ...content, cached: false });
  } catch (error: any) {
    res.status(500).json({ error: `AI generation failed: ${error.message}` });
  }
});

export default router;
