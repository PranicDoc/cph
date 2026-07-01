import { useState } from "react";
import { Plus, X, ChevronRight } from "lucide-react";
import { useAdminClinics, useCreateClinic, type ClinicCreatePayload } from "../../hooks/admin/useAdminClinics";

const inputCls =
  "w-full border border-border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-1 focus:ring-primary";

function ClinicModal({ onClose }: { onClose: () => void }) {
  const createMutation = useCreateClinic();
  const [form, setForm] = useState<ClinicCreatePayload>({ code: "", name: "", phone: "", email: "" });

  const set = (k: keyof ClinicCreatePayload) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(
      { code: form.code, name: form.name, phone: form.phone || undefined, email: form.email || undefined },
      { onSuccess: onClose }
    );
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl shadow-xl p-5 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-text-light hover:text-text">
          <X className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-bold text-text mb-4">New Clinic</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-text-light mb-1">
              Clinic Code <span className="text-red-400">*</span>
            </label>
            <input
              type="text"
              className={inputCls}
              placeholder="e.g. CHNY01 — max 20 chars"
              maxLength={20}
              required
              value={form.code}
              onChange={set("code")}
            />
          </div>
          <div>
            <label className="block text-xs text-text-light mb-1">
              Clinic Name <span className="text-red-400">*</span>
            </label>
            <input
              type="text"
              className={inputCls}
              required
              value={form.name}
              onChange={set("name")}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-text-light mb-1">Phone</label>
              <input type="tel" className={inputCls} value={form.phone} onChange={set("phone")} />
            </div>
            <div>
              <label className="block text-xs text-text-light mb-1">Email</label>
              <input type="email" className={inputCls} value={form.email} onChange={set("email")} />
            </div>
          </div>
          <div className="flex gap-2 pt-1">
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60 transition-colors"
            >
              {createMutation.isPending ? "Creating…" : "Create Clinic"}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 border border-border rounded-lg text-sm text-text hover:bg-background transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function ClinicsPage() {
  const { data: clinics = [], isLoading, isError } = useAdminClinics();
  const [showModal, setShowModal] = useState(false);

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-4 gap-3">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-text">Clinics</h1>
          <p className="text-sm text-text-light mt-0.5">Manage clinic accounts.</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-3 py-2 md:px-4 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark shrink-0 transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">New Clinic</span>
          <span className="sm:hidden">New</span>
        </button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load clinics.</p>}

      {/* Desktop table */}
      {!isLoading && !isError && (
        <>
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-background text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Code</th>
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-left">City</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {clinics.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-text-light">
                      No clinics yet. Create one to get started.
                    </td>
                  </tr>
                ) : (
                  clinics.map((c) => (
                    <tr key={c.id} className="hover:bg-background/60 transition-colors">
                      <td className="px-4 py-3 font-mono text-xs text-text-light">{c.code}</td>
                      <td className="px-4 py-3 font-medium text-text">{c.name}</td>
                      <td className="px-4 py-3 text-xs text-text-light">{c.city || "—"}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                            c.active
                              ? "bg-green-100 text-green-700"
                              : "bg-gray-100 text-gray-500"
                          }`}
                        >
                          {c.active ? "Active" : "Inactive"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-text-light">
                        {new Date(c.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Mobile card list */}
          <div className="md:hidden space-y-3">
            {clinics.length === 0 ? (
              <p className="text-center text-text-light text-sm py-8">
                No clinics yet. Create one to get started.
              </p>
            ) : (
              clinics.map((c) => (
                <div
                  key={c.id}
                  className="border border-border rounded-xl p-4 transition-colors"
                >
                  <div className="flex items-start justify-between mb-1">
                    <span className="font-semibold text-text">{c.name}</span>
                    <ChevronRight className="w-4 h-4 text-text-light shrink-0" />
                  </div>
                  <span className="font-mono text-xs text-text-light">{c.code}</span>
                  <div className="mt-2">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                        c.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
                      }`}
                    >
                      {c.active ? "Active" : "Inactive"}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </>
      )}

      {showModal && <ClinicModal onClose={() => setShowModal(false)} />}
    </div>
  );
}
