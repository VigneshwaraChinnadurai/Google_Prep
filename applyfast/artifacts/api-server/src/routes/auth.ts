import { Router, Request, Response } from "express";

const router = Router();
const APP_PASSWORD = process.env.APP_PASSWORD || "applyfast";

router.post("/login", (req: Request, res: Response) => {
  const { password } = req.body;
  if (password === APP_PASSWORD) {
    (req.session as any).authenticated = true;
    res.json({ authenticated: true });
  } else {
    res.status(401).json({ error: "Invalid credentials" });
  }
});

router.post("/logout", (req: Request, res: Response) => {
  req.session.destroy(() => {
    res.status(204).end();
  });
});

router.get("/me", (req: Request, res: Response) => {
  if (req.session && (req.session as any).authenticated) {
    res.json({ authenticated: true });
  } else {
    res.status(401).json({ authenticated: false });
  }
});

export default router;
