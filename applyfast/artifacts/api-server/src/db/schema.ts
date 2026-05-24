import { sqliteTable, text, integer } from "drizzle-orm/sqlite-core";

export const profileTable = sqliteTable("profile", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  name: text("name").notNull(),
  email: text("email").notNull(),
  phone: text("phone"),
  location: text("location"),
  workAuthorization: text("work_authorization"),
  linkedinUrl: text("linkedin_url"),
  githubUrl: text("github_url"),
  portfolioUrl: text("portfolio_url"),
  skills: text("skills", { mode: "json" }).notNull().$type<string[]>().default([]),
  workHistory: text("work_history", { mode: "json" }).notNull().$type<WorkHistoryEntry[]>().default([]),
  resumeText: text("resume_text"),
  resumeFileName: text("resume_file_name"),
  jobFilters: text("job_filters", { mode: "json" }).notNull().$type<JobFilters>().default({}),
  targetCompanies: text("target_companies", { mode: "json" }).notNull().$type<string[]>().default([]),
  createdAt: text("created_at").notNull().$defaultFn(() => new Date().toISOString()),
  updatedAt: text("updated_at").notNull().$defaultFn(() => new Date().toISOString()),
});

export const jobsTable = sqliteTable("jobs", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  externalId: text("external_id"),
  source: text("source").notNull(),
  title: text("title").notNull(),
  company: text("company").notNull(),
  location: text("location"),
  remote: integer("remote", { mode: "boolean" }).notNull().default(false),
  salary: text("salary"),
  description: text("description").notNull().default(""),
  url: text("url").notNull(),
  boardType: text("board_type"),
  applyEmail: text("apply_email"),
  tags: text("tags", { mode: "json" }).notNull().$type<string[]>().default([]),
  fetchedAt: text("fetched_at").notNull().$defaultFn(() => new Date().toISOString()),
  createdAt: text("created_at").notNull().$defaultFn(() => new Date().toISOString()),
});

export const tailoredContentTable = sqliteTable("tailored_content", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  jobId: integer("job_id").notNull().references(() => jobsTable.id),
  tailoredBullets: text("tailored_bullets", { mode: "json" }).notNull().$type<string[]>().default([]),
  coverLetter: text("cover_letter").notNull().default(""),
  matchScore: integer("match_score").notNull().default(0),
  matchReasoning: text("match_reasoning").notNull().default(""),
  createdAt: text("created_at").notNull().$defaultFn(() => new Date().toISOString()),
});

export const applicationsTable = sqliteTable("applications", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  jobId: integer("job_id").notNull().references(() => jobsTable.id),
  status: text("status").notNull().default("queued"),
  appliedAt: text("applied_at"),
  notes: text("notes"),
  createdAt: text("created_at").notNull().$defaultFn(() => new Date().toISOString()),
  updatedAt: text("updated_at").notNull().$defaultFn(() => new Date().toISOString()),
});

export const emailConfigTable = sqliteTable("email_config", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  host: text("host").notNull(),
  port: integer("port").notNull(),
  user: text("user").notNull(),
  password: text("password").notNull(),
  tls: integer("tls", { mode: "boolean" }).notNull().default(true),
});

// Types
export interface WorkHistoryEntry {
  company: string;
  title: string;
  startDate: string;
  endDate?: string | null;
  description: string;
}

export interface JobFilters {
  keywords?: string | null;
  location?: string | null;
  remoteOnly?: boolean;
  minSalary?: number | null;
  seniority?: string | null;
}
