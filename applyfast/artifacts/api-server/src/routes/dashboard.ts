import { Router, Request, Response } from "express";
import { db, applicationsTable, jobsTable, tailoredContentTable } from "../db/index.js";
import { eq, sql, gte, and, desc } from "drizzle-orm";

const router = Router();

router.get("/stats", async (_req: Request, res: Response) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayStr = today.toISOString();

  const weekAgo = new Date();
  weekAgo.setDate(weekAgo.getDate() - 7);
  const weekAgoStr = weekAgo.toISOString();

  // Today's applied count
  const [{ todayCount }] = await db.select({
    todayCount: sql<number>`count(*)`
  }).from(applicationsTable)
    .where(and(
      eq(applicationsTable.status, "applied"),
      gte(applicationsTable.appliedAt, todayStr)
    ));

  // Weekly count
  const [{ weeklyCount }] = await db.select({
    weeklyCount: sql<number>`count(*)`
  }).from(applicationsTable)
    .where(and(
      eq(applicationsTable.status, "applied"),
      gte(applicationsTable.appliedAt, weekAgoStr)
    ));

  // Average match score
  const [{ avgScore }] = await db.select({
    avgScore: sql<number | null>`avg(match_score)`
  }).from(tailoredContentTable);

  // Pipeline breakdown
  const pipeline = await db.select({
    status: applicationsTable.status,
    count: sql<number>`count(*)`
  }).from(applicationsTable).groupBy(applicationsTable.status);

  // Response rate
  const [{ totalApplied }] = await db.select({
    totalApplied: sql<number>`count(*)`
  }).from(applicationsTable).where(eq(applicationsTable.status, "applied"));

  const [{ responses }] = await db.select({
    responses: sql<number>`count(*)`
  }).from(applicationsTable).where(eq(applicationsTable.status, "response"));

  const responseRate = totalApplied > 0 ? (Number(responses) / Number(totalApplied)) * 100 : null;

  // Recent applications
  const recentApps = await db.select()
    .from(applicationsTable)
    .orderBy(desc(applicationsTable.createdAt))
    .limit(5);

  const recentApplications = await Promise.all(recentApps.map(async (app) => {
    const [job] = await db.select().from(jobsTable).where(eq(jobsTable.id, app.jobId));
    const [tc] = await db.select().from(tailoredContentTable).where(eq(tailoredContentTable.jobId, app.jobId));
    return { ...app, job, tailoredContent: tc || null };
  }));

  res.json({
    todayCount: Number(todayCount),
    todayGoal: 50,
    weeklyCount: Number(weeklyCount),
    avgMatchScore: avgScore ? Number(avgScore) : null,
    responseRate,
    pipeline,
    recentApplications,
  });
});

export default router;
