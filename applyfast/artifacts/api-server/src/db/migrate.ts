import { db } from "./index.js";
import { sql } from "drizzle-orm";

export async function migrate() {
  await db.run(sql`CREATE TABLE IF NOT EXISTS profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    phone TEXT,
    location TEXT,
    work_authorization TEXT,
    linkedin_url TEXT,
    github_url TEXT,
    portfolio_url TEXT,
    skills TEXT NOT NULL DEFAULT '[]',
    work_history TEXT NOT NULL DEFAULT '[]',
    resume_text TEXT,
    resume_file_name TEXT,
    job_filters TEXT NOT NULL DEFAULT '{}',
    target_companies TEXT NOT NULL DEFAULT '[]',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  )`);

  await db.run(sql`CREATE TABLE IF NOT EXISTS jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT,
    source TEXT NOT NULL,
    title TEXT NOT NULL,
    company TEXT NOT NULL,
    location TEXT,
    remote INTEGER NOT NULL DEFAULT 0,
    salary TEXT,
    description TEXT NOT NULL DEFAULT '',
    url TEXT NOT NULL,
    board_type TEXT,
    apply_email TEXT,
    tags TEXT NOT NULL DEFAULT '[]',
    fetched_at TEXT NOT NULL,
    created_at TEXT NOT NULL
  )`);

  await db.run(sql`CREATE TABLE IF NOT EXISTS tailored_content (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id),
    tailored_bullets TEXT NOT NULL DEFAULT '[]',
    cover_letter TEXT NOT NULL DEFAULT '',
    match_score INTEGER NOT NULL DEFAULT 0,
    match_reasoning TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
  )`);

  await db.run(sql`CREATE TABLE IF NOT EXISTS applications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id),
    status TEXT NOT NULL DEFAULT 'queued',
    applied_at TEXT,
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  )`);

  await db.run(sql`CREATE TABLE IF NOT EXISTS email_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host TEXT NOT NULL,
    port INTEGER NOT NULL,
    user TEXT NOT NULL,
    password TEXT NOT NULL,
    tls INTEGER NOT NULL DEFAULT 1
  )`);

  // Create unique index on jobs URL for deduplication
  await db.run(sql`CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_url ON jobs(url)`);
}
