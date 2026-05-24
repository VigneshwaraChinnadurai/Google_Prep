import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { Target, TrendingUp, BarChart3, Activity } from "lucide-react";

export default function DashboardPage() {
  const { data: stats, isLoading } = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => api.getDashboardStats(),
  });

  if (isLoading) {
    return <div className="animate-pulse space-y-4">
      <div className="h-32 bg-accent rounded-lg"></div>
      <div className="grid grid-cols-3 gap-4">
        <div className="h-24 bg-accent rounded-lg"></div>
        <div className="h-24 bg-accent rounded-lg"></div>
        <div className="h-24 bg-accent rounded-lg"></div>
      </div>
    </div>;
  }

  if (!stats) return null;

  const progress = Math.min((stats.todayCount / stats.todayGoal) * 100, 100);

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Dashboard</h2>

      {/* Daily Goal */}
      <div className="bg-card p-6 rounded-lg border border-border">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Target className="text-primary" size={20} />
            <h3 className="font-semibold">Daily Goal</h3>
          </div>
          <span className="text-2xl font-bold text-primary">
            {stats.todayCount}/{stats.todayGoal}
          </span>
        </div>
        <div className="w-full h-3 bg-accent rounded-full overflow-hidden">
          <div
            className="h-full bg-primary rounded-full transition-all duration-500"
            style={{ width: `${progress}%` }}
          />
        </div>
        <p className="text-sm text-muted-foreground mt-2">
          {stats.todayGoal - stats.todayCount > 0
            ? `${stats.todayGoal - stats.todayCount} more to go today!`
            : "🎉 Goal reached!"}
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-card p-4 rounded-lg border border-border">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <TrendingUp size={16} />
            <span className="text-sm">Weekly Apps</span>
          </div>
          <p className="text-2xl font-bold">{stats.weeklyCount}</p>
        </div>

        <div className="bg-card p-4 rounded-lg border border-border">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <BarChart3 size={16} />
            <span className="text-sm">Avg Match Score</span>
          </div>
          <p className="text-2xl font-bold">
            {stats.avgMatchScore != null ? `${Math.round(stats.avgMatchScore)}%` : "—"}
          </p>
        </div>

        <div className="bg-card p-4 rounded-lg border border-border">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <Activity size={16} />
            <span className="text-sm">Response Rate</span>
          </div>
          <p className="text-2xl font-bold">
            {stats.responseRate != null ? `${Math.round(stats.responseRate)}%` : "—"}
          </p>
        </div>
      </div>

      {/* Pipeline */}
      <div className="bg-card p-6 rounded-lg border border-border">
        <h3 className="font-semibold mb-4">Pipeline</h3>
        <div className="flex flex-wrap gap-3">
          {(stats.pipeline || []).map((p: any) => (
            <div key={p.status} className="px-4 py-2 bg-accent rounded-md">
              <span className="text-xs text-muted-foreground capitalize">{p.status}</span>
              <p className="text-lg font-bold">{p.count}</p>
            </div>
          ))}
          {(!stats.pipeline || stats.pipeline.length === 0) && (
            <p className="text-muted-foreground text-sm">No applications yet. Go to Jobs to get started!</p>
          )}
        </div>
      </div>

      {/* Recent Activity */}
      <div className="bg-card p-6 rounded-lg border border-border">
        <h3 className="font-semibold mb-4">Recent Activity</h3>
        {stats.recentApplications?.length > 0 ? (
          <div className="space-y-2">
            {stats.recentApplications.map((app: any) => (
              <div key={app.id} className="flex items-center justify-between py-2 border-b border-border last:border-0">
                <div>
                  <p className="font-medium">{app.job?.title || "Unknown"}</p>
                  <p className="text-sm text-muted-foreground">{app.job?.company || ""}</p>
                </div>
                <span className={`text-xs px-2 py-1 rounded capitalize ${
                  app.status === "applied" ? "bg-green-100 text-green-700" :
                  app.status === "queued" ? "bg-blue-100 text-blue-700" :
                  "bg-gray-100 text-gray-700"
                }`}>
                  {app.status}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-muted-foreground text-sm">No recent activity</p>
        )}
      </div>
    </div>
  );
}
