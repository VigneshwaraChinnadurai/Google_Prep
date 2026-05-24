import { Router, Request, Response } from "express";
import { db, jobsTable } from "../db/index.js";
import { eq, like, and, sql, desc } from "drizzle-orm";

const router = Router();

router.get("/", async (req: Request, res: Response) => {
  const { status = "queued", search, remoteOnly, page = "1", pageSize = "20" } = req.query;
  const offset = (Number(page) - 1) * Number(pageSize);

  const conditions: any[] = [];
  if (search) {
    conditions.push(like(jobsTable.title, `%${search}%`));
  }
  if (remoteOnly === "true") {
    conditions.push(eq(jobsTable.remote, true));
  }

  // Get jobs that match status filter via applications
  const where = conditions.length > 0 ? and(...conditions) : undefined;

  const jobs = await db.select()
    .from(jobsTable)
    .where(where)
    .orderBy(desc(jobsTable.createdAt))
    .limit(Number(pageSize))
    .offset(offset);

  const [{ count }] = await db.select({ count: sql<number>`count(*)` })
    .from(jobsTable)
    .where(where);

  res.json({
    jobs,
    total: Number(count),
    page: Number(page),
    pageSize: Number(pageSize),
  });
});

router.get("/:id", async (req: Request, res: Response) => {
  const id = Number(req.params.id);
  const jobs = await db.select().from(jobsTable).where(eq(jobsTable.id, id));
  if (jobs.length === 0) {
    res.status(404).json({ error: "Job not found" });
    return;
  }
  res.json(jobs[0]);
});

router.post("/fetch", async (_req: Request, res: Response) => {
  let fetched = 0;
  let skipped = 0;
  const sources: string[] = [];

  // 1. Fetch from RemoteOK API
  try {
    const response = await fetch("https://remoteok.com/api");
    const data = await response.json();
    sources.push("remoteok");

    for (const item of data.slice(1)) {
      if (!item.url || !item.position || !item.company) continue;
      const url = item.url || `https://remoteok.com/remote-jobs/${item.id}`;
      try {
        await db.insert(jobsTable).values({
          externalId: String(item.id),
          source: "remoteok",
          title: item.position,
          company: item.company,
          location: item.location || "Remote",
          remote: true,
          salary: item.salary_min ? `$${item.salary_min} - $${item.salary_max}` : null,
          description: item.description || "",
          url,
          boardType: "other",
          tags: item.tags || [],
        });
        fetched++;
      } catch {
        skipped++;
      }
    }
  } catch (e: any) {
    console.error("RemoteOK fetch error:", e.message);
  }

  // 2. Fetch from Jobicy API (remote tech jobs)
  try {
    const jobicyRes = await fetch("https://jobicy.com/api/v2/remote-jobs?count=50&industry=tech&tag=ai,machine-learning,python,data-science");
    const jobicyData = await jobicyRes.json();
    sources.push("jobicy");

    for (const item of jobicyData.jobs || []) {
      if (!item.url || !item.jobTitle || !item.companyName) continue;
      try {
        await db.insert(jobsTable).values({
          externalId: String(item.id),
          source: "jobicy",
          title: item.jobTitle,
          company: item.companyName,
          location: item.jobGeo || "Remote",
          remote: true,
          salary: item.annualSalaryMin ? `$${item.annualSalaryMin} - $${item.annualSalaryMax}` : null,
          description: item.jobDescription || item.jobExcerpt || "",
          url: item.url,
          boardType: "other",
          tags: (item.jobIndustry || []).concat(item.jobType || []),
        });
        fetched++;
      } catch {
        skipped++;
      }
    }
  } catch (e: any) {
    console.error("Jobicy fetch error:", e.message);
  }

  // 3. Fetch from Arbeitnow API (tech remote jobs)
  try {
    const arbeitRes = await fetch("https://www.arbeitnow.com/api/job-board-api?tags=ai,machine+learning,python,data+science&remote=true");
    const arbeitData = await arbeitRes.json();
    sources.push("arbeitnow");

    for (const item of arbeitData.data || []) {
      if (!item.url || !item.title || !item.company_name) continue;
      try {
        await db.insert(jobsTable).values({
          externalId: String(item.slug),
          source: "arbeitnow",
          title: item.title,
          company: item.company_name,
          location: item.location || "Remote",
          remote: item.remote || false,
          salary: null,
          description: item.description || "",
          url: item.url,
          boardType: "other",
          tags: item.tags || [],
        });
        fetched++;
      } catch {
        skipped++;
      }
    }
  } catch (e: any) {
    console.error("Arbeitnow fetch error:", e.message);
  }

  res.json({ fetched, skipped, sources });
});

export default router;
