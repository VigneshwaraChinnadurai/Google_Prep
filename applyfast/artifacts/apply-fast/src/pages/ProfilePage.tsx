import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import { Upload, Save } from "lucide-react";

export default function ProfilePage() {
  const queryClient = useQueryClient();
  const { data: profile, isLoading } = useQuery({
    queryKey: ["profile"],
    queryFn: () => api.getProfile(),
    retry: false,
  });

  const [form, setForm] = useState<any>(null);
  const [resumeStatus, setResumeStatus] = useState("");

  // Initialize form when profile loads
  if (profile && !form) {
    setForm({
      name: profile.name || "",
      email: profile.email || "",
      phone: profile.phone || "",
      location: profile.location || "",
      workAuthorization: profile.workAuthorization || "",
      linkedinUrl: profile.linkedinUrl || "",
      githubUrl: profile.githubUrl || "",
      portfolioUrl: profile.portfolioUrl || "",
      skills: (profile.skills || []).join(", "),
      targetCompanies: (profile.targetCompanies || []).join(", "),
    });
  }

  const saveMutation = useMutation({
    mutationFn: (data: any) => api.updateProfile({
      ...data,
      skills: data.skills.split(",").map((s: string) => s.trim()).filter(Boolean),
      targetCompanies: data.targetCompanies.split(",").map((s: string) => s.trim()).filter(Boolean),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile"] });
    },
  });

  const handleResumeUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setResumeStatus("Uploading...");
    try {
      const result = await api.uploadResume(file);
      setResumeStatus(`✓ Parsed ${result.textLength} characters from ${result.fileName}`);
      queryClient.invalidateQueries({ queryKey: ["profile"] });
    } catch (err: any) {
      setResumeStatus(`Error: ${err.message}`);
    }
  };

  if (isLoading) {
    return <div className="animate-pulse space-y-4">
      <div className="h-8 bg-accent rounded w-48"></div>
      <div className="h-64 bg-accent rounded-lg"></div>
    </div>;
  }

  if (!form) {
    setForm({
      name: "", email: "", phone: "", location: "", workAuthorization: "",
      linkedinUrl: "", githubUrl: "", portfolioUrl: "", skills: "", targetCompanies: "",
    });
    return null;
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <h2 className="text-2xl font-bold">Profile & Resume</h2>

      {/* Resume Upload */}
      <div className="bg-card p-6 rounded-lg border border-border">
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Upload size={18} /> Resume Upload
        </h3>
        <input
          type="file"
          accept=".pdf,.docx"
          onChange={handleResumeUpload}
          className="block w-full text-sm text-muted-foreground file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-primary file:text-primary-foreground hover:file:opacity-90"
        />
        {resumeStatus && <p className="text-sm mt-2 text-muted-foreground">{resumeStatus}</p>}
        {profile?.resumeFileName && !resumeStatus && (
          <p className="text-sm mt-2 text-muted-foreground">Current: {profile.resumeFileName}</p>
        )}
      </div>

      {/* Profile Form */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          saveMutation.mutate(form);
        }}
        className="bg-card p-6 rounded-lg border border-border space-y-4"
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium mb-1">Name *</label>
            <input
              type="text"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Email *</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Phone</label>
            <input
              type="text"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Location</label>
            <input
              type="text"
              value={form.location}
              onChange={(e) => setForm({ ...form, location: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Work Authorization</label>
            <input
              type="text"
              value={form.workAuthorization}
              onChange={(e) => setForm({ ...form, workAuthorization: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">LinkedIn URL</label>
            <input
              type="url"
              value={form.linkedinUrl}
              onChange={(e) => setForm({ ...form, linkedinUrl: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">GitHub URL</label>
            <input
              type="url"
              value={form.githubUrl}
              onChange={(e) => setForm({ ...form, githubUrl: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Portfolio URL</label>
            <input
              type="url"
              value={form.portfolioUrl}
              onChange={(e) => setForm({ ...form, portfolioUrl: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Skills (comma-separated)</label>
          <input
            type="text"
            value={form.skills}
            onChange={(e) => setForm({ ...form, skills: e.target.value })}
            className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            placeholder="Python, React, AWS, Machine Learning"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Target Companies (comma-separated)</label>
          <input
            type="text"
            value={form.targetCompanies}
            onChange={(e) => setForm({ ...form, targetCompanies: e.target.value })}
            className="w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            placeholder="Google, Microsoft, Amazon"
          />
        </div>

        <button
          type="submit"
          disabled={saveMutation.isPending}
          className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-md hover:opacity-90 disabled:opacity-50"
        >
          <Save size={16} />
          {saveMutation.isPending ? "Saving..." : "Save Profile"}
        </button>
        {saveMutation.isSuccess && <p className="text-sm text-green-600">✓ Profile saved!</p>}
      </form>
    </div>
  );
}
