import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";

export default function LoginPage() {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const queryClient = useQueryClient();

  const loginMutation = useMutation({
    mutationFn: (pw: string) => api.login(pw),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["auth"] });
    },
    onError: () => {
      setError("Invalid password");
    },
  });

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted">
      <div className="bg-card p-8 rounded-lg shadow-lg w-full max-w-sm border border-border">
        <div className="text-center mb-6">
          <h1 className="text-2xl font-bold text-primary">⚡ ApplyFast</h1>
          <p className="text-muted-foreground text-sm mt-2">
            Submit 50+ tailored applications per day
          </p>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            setError("");
            loginMutation.mutate(password);
          }}
        >
          <label className="block text-sm font-medium mb-2">Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            placeholder="Enter password"
            autoFocus
          />
          {error && <p className="text-destructive text-sm mt-2">{error}</p>}
          <button
            type="submit"
            disabled={loginMutation.isPending}
            className="w-full mt-4 bg-primary text-primary-foreground py-2 rounded-md hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {loginMutation.isPending ? "Logging in..." : "Login"}
          </button>
        </form>
      </div>
    </div>
  );
}
