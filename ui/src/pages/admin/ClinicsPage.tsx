import { Fragment, useState } from "react";
import { Plus, X, Pencil, ChevronDown, ChevronRight, Users } from "lucide-react";
import {
  useAdminClinics, useCreateClinic, useUpdateClinic, useToggleClinicActive,
  type ClinicItem, type ClinicCreatePayload, type ClinicUpdatePayload,
} from "../../hooks/admin/useAdminClinics";
import { useClinicHealersByClinicId, useToggleHealerActive } from "../../hooks/admin/useAdminHealers";

const inputCls =
  "w-full border border-border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-1 focus:ring-primary";

/* ── Create modal ── */
function CreateClinicModal({ onClose }: { onClose: () => void }) {
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
              type="text" className={inputCls} placeholder="e.g. CHNY01 — max 20 chars"
              maxLength={20} required value={form.code} onChange={set("code")}
            />
          </div>
          <div>
            <label className="block text-xs text-text-light mb-1">
              Clinic Name <span className="text-red-400">*</span>
            </label>
            <input type="text" className={inputCls} required value={form.name} onChange={set("name")} />
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
              type="submit" disabled={createMutation.isPending}
              className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60 transition-colors"
            >
              {createMutation.isPending ? "Creating…" : "Create Clinic"}
            </button>
            <button
              type="button" onClick={onClose}
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

/* ── Edit modal ── */
function EditClinicModal({ clinic, onClose }: { clinic: ClinicItem; onClose: () => void }) {
  const updateMutation = useUpdateClinic(clinic.id);
  const [form, setForm] = useState<ClinicUpdatePayload>({
    code: clinic.code,
    name: clinic.name,
    phone: clinic.phone ?? "",
    email: clinic.email ?? "",
    city: clinic.city ?? "",
    state: clinic.state ?? "",
  });

  const set = (k: keyof ClinicUpdatePayload) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateMutation.mutate(
      {
        code: form.code,
        name: form.name,
        phone: form.phone || undefined,
        email: form.email || undefined,
        city: form.city || undefined,
        state: form.state || undefined,
      },
      { onSuccess: onClose }
    );
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl shadow-xl p-5 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-text-light hover:text-text">
          <X className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-bold text-text mb-4">Edit Clinic</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-text-light mb-1">Clinic Code</label>
            <input
              type="text" className={`${inputCls} bg-background text-text-light cursor-not-allowed`}
              value={form.code} readOnly disabled
            />
          </div>
          <div>
            <label className="block text-xs text-text-light mb-1">
              Clinic Name <span className="text-red-400">*</span>
            </label>
            <input type="text" className={inputCls} required value={form.name} onChange={set("name")} />
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
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-text-light mb-1">City</label>
              <input type="text" className={inputCls} value={form.city} onChange={set("city")} />
            </div>
            <div>
              <label className="block text-xs text-text-light mb-1">State</label>
              <input type="text" className={inputCls} value={form.state} onChange={set("state")} />
            </div>
          </div>
          <div className="flex gap-2 pt-1">
            <button
              type="submit" disabled={updateMutation.isPending}
              className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60 transition-colors"
            >
              {updateMutation.isPending ? "Saving…" : "Save Changes"}
            </button>
            <button
              type="button" onClick={onClose}
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

/* ── Healer sub-row (SUPADM expanded view) ── */
function ClinicHealerRows({ clinicId }: { clinicId: string }) {
  const { data: healers = [], isLoading } = useClinicHealersByClinicId(clinicId);
  const toggleActive = useToggleHealerActive();

  if (isLoading) {
    return (
      <tr>
        <td colSpan={6} className="px-8 py-3 bg-background text-xs text-text-light">Loading healers…</td>
      </tr>
    );
  }
  if (healers.length === 0) {
    return (
      <tr>
        <td colSpan={6} className="px-8 py-3 bg-background text-xs text-text-light">No healers in this clinic.</td>
      </tr>
    );
  }
  return (
    <>
      {healers.map((h) => (
        <tr key={h.id} className="bg-background/70 border-t border-border/50">
          <td className="px-8 py-2.5 text-xs text-text-light" colSpan={2}>
            <div className="font-medium text-text">{h.fullName}</div>
            <div className="text-text-light">{h.email}</div>
          </td>
          <td className="px-4 py-2.5 text-xs text-text-light">{h.role}</td>
          <td className="px-4 py-2.5">
            <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${h.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
              {h.active ? "Active" : "Inactive"}
            </span>
          </td>
          <td className="px-4 py-2.5 text-xs text-text-light">{new Date(h.createdAt).toLocaleDateString()}</td>
          <td className="px-4 py-2.5">
            <button
              onClick={() => toggleActive.mutate({ id: h.id, active: !h.active })}
              disabled={toggleActive.isPending}
              className={`px-2.5 py-1 rounded text-xs font-medium transition-colors disabled:opacity-50 ${
                h.active
                  ? "text-red-600 hover:bg-red-50"
                  : "text-green-600 hover:bg-green-50"
              }`}
            >
              {h.active ? "Deactivate" : "Activate"}
            </button>
          </td>
        </tr>
      ))}
    </>
  );
}

/* ── Main page ── */
export default function ClinicsPage() {
  const { data: clinics = [], isLoading, isError } = useAdminClinics();
  const toggleClinicActive = useToggleClinicActive();

  const [showCreate, setShowCreate] = useState(false);
  const [editingClinic, setEditingClinic] = useState<ClinicItem | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const toggleExpand = (id: string) => setExpandedId((prev) => (prev === id ? null : id));

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-4 gap-3">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-text">Clinics</h1>
          <p className="text-sm text-text-light mt-0.5">Manage clinic accounts.</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-3 py-2 md:px-4 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark shrink-0 transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">New Clinic</span>
          <span className="sm:hidden">New</span>
        </button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load clinics.</p>}

      {!isLoading && !isError && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-background text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Code</th>
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-left">City</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Created</th>
                  <th className="px-4 py-3 text-left">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {clinics.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-text-light">
                      No clinics yet. Create one to get started.
                    </td>
                  </tr>
                ) : (
                  clinics.map((c) => (
                    <Fragment key={c.id}>
                      <tr
                        className="hover:bg-background/60 transition-colors cursor-pointer"
                        onClick={() => toggleExpand(c.id)}
                      >
                        <td className="px-4 py-3 font-mono text-xs text-text-light">{c.code}</td>
                        <td className="px-4 py-3 font-medium text-text">
                          <span className="flex items-center gap-1.5">
                            {expandedId === c.id
                              ? <ChevronDown className="w-3.5 h-3.5 text-text-light shrink-0" />
                              : <ChevronRight className="w-3.5 h-3.5 text-text-light shrink-0" />}
                            {c.name}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-text-light">{c.city || "—"}</td>
                        <td className="px-4 py-3">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${c.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                            {c.active ? "Active" : "Inactive"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-text-light">
                          {new Date(c.createdAt).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                          <div className="flex items-center gap-1.5">
                            <button
                              onClick={() => setEditingClinic(c)}
                              className="p-1.5 rounded hover:bg-primary/10 text-text-light hover:text-primary transition-colors"
                              title="Edit"
                            >
                              <Pencil className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => toggleExpand(c.id)}
                              className="p-1.5 rounded hover:bg-primary/10 text-text-light hover:text-primary transition-colors"
                              title="Healers"
                            >
                              <Users className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => toggleClinicActive.mutate({ id: c.id, active: !c.active })}
                              disabled={toggleClinicActive.isPending}
                              className={`px-2 py-1 rounded text-xs font-medium transition-colors disabled:opacity-50 ${
                                c.active ? "text-red-600 hover:bg-red-50" : "text-green-600 hover:bg-green-50"
                              }`}
                            >
                              {c.active ? "Deactivate" : "Activate"}
                            </button>
                          </div>
                        </td>
                      </tr>
                      {expandedId === c.id && <ClinicHealerRows clinicId={c.id} />}
                    </Fragment>
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
                <div key={c.id} className="border border-border rounded-xl p-4">
                  <div className="flex items-start justify-between mb-1">
                    <div>
                      <span className="font-semibold text-text">{c.name}</span>
                      <span className="font-mono text-xs text-text-light block mt-0.5">{c.code}</span>
                    </div>
                    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${c.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                      {c.active ? "Active" : "Inactive"}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-3 pt-3 border-t border-border">
                    <button
                      onClick={() => setEditingClinic(c)}
                      className="flex items-center gap-1 px-3 py-1.5 border border-border rounded-lg text-xs text-text hover:bg-background transition-colors"
                    >
                      <Pencil className="w-3 h-3" /> Edit
                    </button>
                    <button
                      onClick={() => toggleExpand(c.id)}
                      className="flex items-center gap-1 px-3 py-1.5 border border-border rounded-lg text-xs text-text hover:bg-background transition-colors"
                    >
                      <Users className="w-3 h-3" /> Healers
                    </button>
                    <button
                      onClick={() => toggleClinicActive.mutate({ id: c.id, active: !c.active })}
                      disabled={toggleClinicActive.isPending}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors disabled:opacity-50 ${
                        c.active ? "text-red-600 border border-red-200 hover:bg-red-50" : "text-green-600 border border-green-200 hover:bg-green-50"
                      }`}
                    >
                      {c.active ? "Deactivate" : "Activate"}
                    </button>
                  </div>
                  {expandedId === c.id && <MobileHealerList clinicId={c.id} />}
                </div>
              ))
            )}
          </div>
        </>
      )}

      {showCreate && <CreateClinicModal onClose={() => setShowCreate(false)} />}
      {editingClinic && <EditClinicModal clinic={editingClinic} onClose={() => setEditingClinic(null)} />}
    </div>
  );
}

/* ── Mobile healer list ── */
function MobileHealerList({ clinicId }: { clinicId: string }) {
  const { data: healers = [], isLoading } = useClinicHealersByClinicId(clinicId);
  const toggleActive = useToggleHealerActive();

  if (isLoading) return <p className="text-xs text-text-light mt-3">Loading healers…</p>;
  if (healers.length === 0) return <p className="text-xs text-text-light mt-3">No healers in this clinic.</p>;

  return (
    <div className="mt-3 pt-3 border-t border-border space-y-2">
      {healers.map((h) => (
        <div key={h.id} className="flex items-center justify-between py-1">
          <div>
            <p className="text-xs font-medium text-text">{h.fullName}</p>
            <p className="text-xs text-text-light">{h.role}</p>
          </div>
          <button
            onClick={() => toggleActive.mutate({ id: h.id, active: !h.active })}
            disabled={toggleActive.isPending}
            className={`px-2.5 py-1 rounded text-xs font-medium transition-colors disabled:opacity-50 ${
              h.active ? "text-red-600 hover:bg-red-50" : "text-green-600 hover:bg-green-50"
            }`}
          >
            {h.active ? "Deactivate" : "Activate"}
          </button>
        </div>
      ))}
    </div>
  );
}
