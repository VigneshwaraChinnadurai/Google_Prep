import "dotenv/config";
import express from "express";
import cors from "cors";
import session from "express-session";
import { migrate } from "./db/migrate.js";
import { requireAuth } from "./middlewares/auth.js";
import authRouter from "./routes/auth.js";
import profileRouter from "./routes/profile.js";
import jobsRouter from "./routes/jobs.js";
import applicationsRouter from "./routes/applications.js";
import aiRouter from "./routes/ai.js";
import dashboardRouter from "./routes/dashboard.js";

const app = express();
const PORT = Number(process.env.PORT) || 8080;

// Run migrations
await migrate();
console.log("✓ Database initialized");

// Middleware
app.use(cors({
  origin: process.env.FRONTEND_URL || "http://localhost:5173",
  credentials: true,
}));
app.use(express.json());
app.use(session({
  secret: process.env.SESSION_SECRET || "applyfast-dev-secret",
  resave: false,
  saveUninitialized: false,
  cookie: {
    secure: false,
    httpOnly: true,
    maxAge: 24 * 60 * 60 * 1000, // 24 hours
  },
}));

// Health check
app.get("/api/healthz", (_req, res) => {
  res.json({ status: "ok" });
});

// Public routes
app.use("/api/auth", authRouter);

// Protected routes
app.use("/api/profile", requireAuth, profileRouter);
app.use("/api/jobs", requireAuth, jobsRouter);
app.use("/api/applications", requireAuth, applicationsRouter);
app.use("/api/ai", requireAuth, aiRouter);
app.use("/api/dashboard", requireAuth, dashboardRouter);

app.listen(PORT, () => {
  console.log(`✓ ApplyFast API server running on http://localhost:${PORT}`);
});
