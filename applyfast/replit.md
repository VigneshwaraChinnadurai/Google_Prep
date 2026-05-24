# ApplyFast

A single-user job application assistant that helps submit 50+ tailored applications per day, powered by Google Gemini AI.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 8080)
- `pnpm --filter @workspace/apply-fast run dev` — run the React frontend (port 25936)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)

## Default Login

- Password: `applyfast` (override with `APP_PASSWORD` env var)

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- Frontend: React + Vite + Tailwind CSS + shadcn/ui + Wouter
- API: Express 5 + express-session
- DB: PostgreSQL + Drizzle ORM
- AI: Google Gemini via `@google/genai` (`gemini-3-flash-preview`)
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)
- File parsing: pdf-parse (PDF), mammoth (DOCX)

## Where things live

- `lib/api-spec/openapi.yaml` — API contract (source of truth)
- `lib/db/src/schema/` — Drizzle tables: profile, jobs, tailored_content, applications
- `artifacts/api-server/src/routes/` — Express route handlers (auth, profile, jobs, ai, applications, dashboard)
- `artifacts/api-server/src/middlewares/auth.ts` — `requireAuth` middleware
- `artifacts/apply-fast/src/` — React frontend

## Architecture decisions

- Single-user auth: session-based with express-session; password set via `APP_PASSWORD` env var (default: `applyfast`)
- AI tailoring is cached in DB per job — same job won't be re-billed on repeat requests
- Resume text is stored in the profile table and sent to Gemini with each tailoring request
- Job deduplication is by URL to avoid storing the same listing twice across fetches
- Gemini structured output (`responseMimeType: "application/json"`) for reliable JSON parsing

## Product

- **Profile & Resume**: Upload PDF/DOCX resume (text extracted server-side), fill profile form with work history and skills, set job search filters
- **Job Aggregation**: Fetch live jobs from RemoteOK (more sources can be added), auto-deduplicates by URL
- **AI Tailoring**: Per-job Gemini call generates 3 tailored resume bullets, a 150-word cover letter, and a match score 0–100 with reasoning. Results are cached.
- **Review & Apply UI**: Job cards + detail view with apply logic: Greenhouse/Lever opens prefill link, email boards open mailto, otherwise copies cover letter + opens URL
- **Dashboard**: Daily 50-app goal tracker, pipeline breakdown, weekly stats, recent activity

## User preferences

- Uses own Google API key (`GOOGLE_API_KEY` env var)
- Model: `gemini-3-flash-preview`

## Gotchas

- Always run `pnpm --filter @workspace/api-spec run codegen` after changing `openapi.yaml`
- After schema changes run `pnpm --filter @workspace/db run push`
- The api-server build bundles everything via esbuild — restart workflow after any server changes
- `@google/genai` and `protobufjs` build scripts must be approved via `pnpm approve-builds`

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
