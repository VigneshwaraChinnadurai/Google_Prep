import { Router, Request, Response } from "express";
import { db, applicationsTable, jobsTable, tailoredContentTable } from "../db/index.js";
import { eq, desc, sql, and } from "drizzle-orm";

const router = Router();

router.get("/", async (req: Request, res: Response) => {
  const { status, page = "1", pageSize = "20" } = req.query;
  const offset = (Number(page) - 1) * Number(pageSize);

  const conditions: any[] = [];
  if (status && status !== "null") {
    conditions.push(eq(applicationsTable.status, status as string));
  }
  const where = conditions.length > 0 ? and(...conditions) : undefined;

  const applications = await db.select()
    .from(applicationsTable)
    .where(where)
    .orderBy(desc(applicationsTable.createdAt))
    .limit(Number(pageSize))
    .offset(offset);

  // Enrich with job and tailored content
  const enriched = await Promise.all(applications.map(async (app) => {
    const [job] = await db.select().from(jobsTable).where(eq(jobsTable.id, app.jobId));
    const [tc] = await db.select().from(tailoredContentTable).where(eq(tailoredContentTable.jobId, app.jobId));
    return { ...app, job, tailoredContent: tc || null };
  }));

  const [{ count }] = await db.select({ count: sql<number>`count(*)` })
    .from(applicationsTable)
    .where(where);

  res.json({
    applications: enriched,
    total: Number(count),
    page: Number(page),
    pageSize: Number(pageSize),
  });
});

router.get("/:id", async (req: Request, res: Response) => {
  const id = Number(req.params.id);
  const [app] = await db.select().from(applicationsTable).where(eq(applicationsTable.id, id));
  if (!app) {
    res.status(404).json({ error: "Application not found" });
    return;
  }
  const [job] = await db.select().from(jobsTable).where(eq(jobsTable.id, app.jobId));
  const [tc] = await db.select().from(tailoredContentTable).where(eq(tailoredContentTable.jobId, app.jobId));
  res.json({ ...app, job, tailoredContent: tc || null });
});

router.patch("/:id", async (req: Request, res: Response) => {
  const id = Number(req.params.id);
  const { status, notes } = req.body;

  const updates: any = { status, updatedAt: new Date().toISOString() };
  if (status === "applied") {
    updates.appliedAt = new Date().toISOString();
  }
  if (notes !== undefined) {
    updates.notes = notes;
  }

  const [app] = await db.update(applicationsTable)
    .set(updates)
    .where(eq(applicationsTable.id, id))
    .returning();

  if (!app) {
    res.status(404).json({ error: "Application not found" });
    return;
  }
  res.json(app);
});

router.post("/by-job/:jobId", async (req: Request, res: Response) => {
  const jobId = Number(req.params.jobId);

  // Check if job exists
  const [job] = await db.select().from(jobsTable).where(eq(jobsTable.id, jobId));
  if (!job) {
    res.status(404).json({ error: "Job not found" });
    return;
  }

  // Check if application already exists
  const [existing] = await db.select().from(applicationsTable)
    .where(eq(applicationsTable.jobId, jobId));
  if (existing) {
    res.json(existing);
    return;
  }

  const [app] = await db.insert(applicationsTable).values({ jobId }).returning();
  res.status(201).json(app);
});

export default router;
