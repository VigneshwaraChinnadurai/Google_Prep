import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import { RefreshCw, Sparkles, ExternalLink, Copy, CheckCircle, Search } from "lucide-react";

export default function JobsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [tailoring, setTailoring] = useState<number | null>(null);
  const [tailoredData, setTailoredData] = useState<Record<number, any>>({});
  const [copiedId, setCopiedId] = useState<number | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["jobs", search, page],
    queryFn: () => api.listJobs({ search, page: String(page), pageSize: "10" }),
  });

  const fetchMutation = useMutation({
    mutationFn: () => api.fetchJobs(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["jobs"] });
    },
  });

  const handleTailor = async (jobId: number) => {
    setTailoring(jobId);
    try {
      const result = await api.tailorJob(jobId);
      setTailoredData({ ...tailoredData, [jobId]: result });
    } catch (err: any) {
      alert(err.message);
    }
    setTailoring(null);
  };

  const handleApply = async (jobId: number, url: string) => {
    await api.createApplication(jobId);
    const apps = await api.listApplications();
    const app = apps.applications.find((a: any) => a.jobId === jobId);
    if (app) {
      await api.updateApplicationStatus(app.id, { status: "applied" });
    }
    queryClient.invalidateQueries({ queryKey: ["jobs"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    window.open(url, "_blank");
  };

  const handleCopy = (jobId: number, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(jobId);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">Jobs</h2>
        <button
          onClick={() => fetchMutation.mutate()}
          disabled={fetchMutation.isPending}
          className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-md hover:opacity-90 disabled:opacity-50"
        >
          <RefreshCw size={16} className={fetchMutation.isPending ? "animate-spin" : ""} />
          {fetchMutation.isPending ? "Fetching..." : "Fetch Jobs"}
        </button>
      </div>

      {fetchMutation.isSuccess && (
        <p className="text-sm text-green-600">
          ✓ Fetched {fetchMutation.data.fetched} new jobs ({fetchMutation.data.skipped} skipped as duplicates)
        </p>
      )}

      {/* Search */}
      <div className="relative">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1); }}
          placeholder="Search jobs by title..."
          className="w-full pl-10 pr-4 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
        />
      </div>

      {/* Job List */}
      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => <div key={i} className="h-32 bg-accent animate-pulse rounded-lg" />)}
        </div>
      ) : data?.jobs?.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          <p>No jobs found. Click "Fetch Jobs" to pull from RemoteOK.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {data?.jobs.map((job: any) => (
            <div key={job.id} className="bg-card p-5 rounded-lg border border-border">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-semibold text-lg">{job.title}</h3>
                  <p className="text-muted-foreground">{job.company} • {job.location || "Remote"}</p>
                  {job.salary && <p className="text-sm text-green-600 mt-1">{job.salary}</p>}
                  <div className="flex flex-wrap gap-1 mt-2">
                    {(job.tags || []).slice(0, 5).map((tag: string) => (
                      <span key={tag} className="text-xs bg-accent px-2 py-0.5 rounded">{tag}</span>
                    ))}
                  </div>
                </div>
                <div className="flex flex-col gap-2">
                  <button
                    onClick={() => handleTailor(job.id)}
                    disabled={tailoring === job.id}
                    className="flex items-center gap-1 text-sm bg-purple-100 text-purple-700 px-3 py-1.5 rounded-md hover:bg-purple-200 disabled:opacity-50"
                  >
                    <Sparkles size={14} />
                    {tailoring === job.id ? "Tailoring..." : "AI Tailor"}
                  </button>
                  <button
                    onClick={() => handleApply(job.id, job.url)}
                    className="flex items-center gap-1 text-sm bg-green-100 text-green-700 px-3 py-1.5 rounded-md hover:bg-green-200"
                  >
                    <ExternalLink size={14} />
                    Apply
                  </button>
                </div>
              </div>

              {/* Tailored Content */}
              {tailoredData[job.id] && (
                <div className="mt-4 pt-4 border-t border-border">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`text-sm font-medium px-2 py-0.5 rounded ${
                      tailoredData[job.id].matchScore >= 70 ? "bg-green-100 text-green-700" :
                      tailoredData[job.id].matchScore >= 40 ? "bg-yellow-100 text-yellow-700" :
                      "bg-red-100 text-red-700"
                    }`}>
                      Match: {tailoredData[job.id].matchScore}%
                    </span>
                    {tailoredData[job.id].cached && (
                      <span className="text-xs text-muted-foreground">(cached)</span>
                    )}
                  </div>
                  <p className="text-sm text-muted-foreground mb-2">{tailoredData[job.id].matchReasoning}</p>

                  <h4 className="text-sm font-medium mb-1">Tailored Bullets:</h4>
                  <ul className="list-disc list-inside text-sm space-y-1 mb-3">
                    {tailoredData[job.id].tailoredBullets.map((b: string, i: number) => (
                      <li key={i}>{b}</li>
                    ))}
                  </ul>

                  <h4 className="text-sm font-medium mb-1">Cover Letter:</h4>
                  <p className="text-sm bg-accent p-3 rounded">{tailoredData[job.id].coverLetter}</p>

                  <button
                    onClick={() => handleCopy(job.id, tailoredData[job.id].coverLetter)}
                    className="flex items-center gap-1 mt-2 text-xs text-muted-foreground hover:text-foreground"
                  >
                    {copiedId === job.id ? <CheckCircle size={12} /> : <Copy size={12} />}
                    {copiedId === job.id ? "Copied!" : "Copy cover letter"}
                  </button>
                </div>
              )}
            </div>
          ))}

          {/* Pagination */}
          {data && data.total > 10 && (
            <div className="flex items-center justify-center gap-4 pt-4">
              <button
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page === 1}
                className="px-3 py-1 border border-border rounded disabled:opacity-30"
              >
                Previous
              </button>
              <span className="text-sm text-muted-foreground">
                Page {page} of {Math.ceil(data.total / 10)}
              </span>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={page * 10 >= data.total}
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
