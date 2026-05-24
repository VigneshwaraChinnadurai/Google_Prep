import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";

const STATUS_OPTIONS = [
  { value: "", label: "All" },
  { value: "queued", label: "Queued" },
  { value: "applied", label: "Applied" },
  { value: "response", label: "Response" },
  { value: "interview", label: "Interview" },
  { value: "offer", label: "Offer" },
  { value: "rejected", label: "Rejected" },
  { value: "skipped", label: "Skipped" },
];

export default function ApplicationsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(1);

  const { data, isLoading } = useQuery({
    queryKey: ["applications", statusFilter, page],
    queryFn: () => api.listApplications({
      ...(statusFilter ? { status: statusFilter } : {}),
      page: String(page),
      pageSize: "15",
    }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      api.updateApplicationStatus(id, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["applications"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Applications</h2>

      {/* Status Filter */}
      <div className="flex gap-2 flex-wrap">
        {STATUS_OPTIONS.map(opt => (
          <button
            key={opt.value}
            onClick={() => { setStatusFilter(opt.value); setPage(1); }}
            className={`px-3 py-1.5 text-sm rounded-md border transition-colors ${
              statusFilter === opt.value
                ? "bg-primary text-primary-foreground border-primary"
                : "border-border hover:bg-accent"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* Applications List */}
      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => <div key={i} className="h-20 bg-accent animate-pulse rounded-lg" />)}
        </div>
      ) : data?.applications?.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          <p>No applications found. Apply to jobs from the Jobs page!</p>
        </div>
      ) : (
        <div className="space-y-3">
          {data?.applications.map((app: any) => (
            <div key={app.id} className="bg-card p-4 rounded-lg border border-border flex items-center justify-between">
              <div>
                <h3 className="font-medium">{app.job?.title || "Unknown Job"}</h3>
                <p className="text-sm text-muted-foreground">
                  {app.job?.company || ""} • {app.job?.location || "Remote"}
                </p>
                {app.appliedAt && (
                  <p className="text-xs text-muted-foreground mt-1">
                    Applied: {new Date(app.appliedAt).toLocaleDateString()}
                  </p>
                )}
                {app.tailoredContent && (
                  <span className="text-xs text-purple-600">
                    Match: {app.tailoredContent.matchScore}%
                  </span>
                )}
              </div>

              <div className="flex items-center gap-2">
                <select
                  value={app.status}
                  onChange={(e) => updateMutation.mutate({ id: app.id, status: e.target.value })}
                  className="text-sm border border-border rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  {STATUS_OPTIONS.filter(o => o.value).map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>

                {app.job?.url && (
                  <a
                    href={app.job.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-sm text-primary hover:underline"
                  >
                    View
                  </a>
                )}
              </div>
            </div>
          ))}

          {/* Pagination */}
          {data && data.total > 15 && (
            <div className="flex items-center justify-center gap-4 pt-4">
              <button
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page === 1}
                className="px-3 py-1 border border-border rounded disabled:opacity-30"
              >
                Previous
              </button>
              <span className="text-sm text-muted-foreground">
                Page {page} of {Math.ceil(data.total / 15)}
              </span>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={page * 15 >= data.total}
                className="px-3 py-1 border border-border rounded disabled:opacity-30"
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
