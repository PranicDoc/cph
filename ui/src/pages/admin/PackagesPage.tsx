import { useState } from "react";
import {
  useAdminPackages,
  useCreatePackage,
  useUpdatePackage,
  useDeactivatePackage,
} from "../../hooks/admin/useAdminPackages";
import type { AdminPackage, CreatePackagePayload } from "../../hooks/admin/useAdminPackages";
import { Plus, Pencil, Trash2, X } from "lucide-react";
import { cn } from "../../lib/utils";

const EMPTY_FORM: CreatePackagePayload = { name: "", credits: 0, price_paise: 0, currency: "INR", display_order: 0 };

function PackageModal({ initial, onClose }: { initial?: AdminPackage; onClose: () => void }) {
  const [form, setForm] = useState<CreatePackagePayload>(
    initial
      ? { name: initial.name, credits: initial.credits, price_paise: initial.price_paise, currency: initial.currency, display_order: initial.display_order }
      : EMPTY_FORM
  );
  const [error, setError] = useState("");
  const create = useCreatePackage();
  const update = useUpdatePackage();

  const set = (key: keyof CreatePackagePayload) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({
      ...f,
      [key]: ["credits", "price_paise", "display_order"].includes(key) ? parseInt(e.target.value, 10) || 0 : e.target.value,
    }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      initial ? await update.mutateAsync({ id: initial.id, payload: form }) : await create.mutateAsync(form);
      onClose();
    } catch {
      setError("Failed to save package.");
    }
  };

  const isPending = create.isPending || update.isPending;

  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl shadow-xl p-5 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-text-light hover:text-text">
          <X className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-bold text-text mb-4">{initial ? "Edit Package" : "New Package"}</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-text-light mb-1">Name</label>
            <input type="text" value={form.name} onChange={set("name")}
              className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" required />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-text-light mb-1">Credits</label>
              <input type="number" value={form.credits} onChange={set("credits")} min="1"
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" required />
            </div>
            <div>
              <label className="block text-xs text-text-light mb-1">Price (paise)</label>
              <input type="number" value={form.price_paise} onChange={set("price_paise")} min="0"
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" required />
              <p className="text-xs text-text-light mt-0.5">₹{((form.price_paise || 0) / 100).toFixed(2)}</p>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-text-light mb-1">Currency</label>
              <input type="text" value={form.currency ?? "INR"} onChange={set("currency")} maxLength={3}
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" />
            </div>
            <div>
              <label className="block text-xs text-text-light mb-1">Display Order</label>
              <input type="number" value={form.display_order} onChange={set("display_order")}
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" />
            </div>
          </div>
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <div className="flex gap-2 pt-1">
            <button type="submit" disabled={isPending}
              className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60">
              {isPending ? "Saving…" : initial ? "Save Changes" : "Create Package"}
            </button>
            <button type="button" onClick={onClose}
              className="px-4 py-2.5 border border-border rounded-lg text-sm text-text hover:bg-surface">
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function PackagesPage() {
  const { data, isLoading, isError } = useAdminPackages();
  const deactivate = useDeactivatePackage();
  const [modalPkg, setModalPkg] = useState<AdminPackage | null | "new">(null);

  return (
    <div>
      <div className="flex items-start justify-between mb-4 gap-3">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-text">Credit Packages</h1>
          <p className="text-sm text-text-light mt-0.5">Manage packages available to healers and clinics.</p>
        </div>
        <button onClick={() => setModalPkg("new")}
          className="flex items-center gap-2 px-3 py-2 md:px-4 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark shrink-0">
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">New Package</span>
          <span className="sm:hidden">New</span>
        </button>
      </div>

      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load packages.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-right">Credits</th>
                  <th className="px-4 py-3 text-right">Price</th>
                  <th className="px-4 py-3 text-left">Currency</th>
                  <th className="px-4 py-3 text-right">Order</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.map((pkg) => (
                  <tr key={pkg.id} className="hover:bg-surface/50">
                    <td className="px-4 py-3 font-semibold text-text">{pkg.name}</td>
                    <td className="px-4 py-3 text-right">{pkg.credits}</td>
                    <td className="px-4 py-3 text-right font-mono">₹{(pkg.price_paise / 100).toFixed(0)}</td>
                    <td className="px-4 py-3 text-text-light">{pkg.currency}</td>
                    <td className="px-4 py-3 text-right">{pkg.display_order}</td>
                    <td className="px-4 py-3">
                      <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold",
                        pkg.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500")}>
                        {pkg.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-1 justify-end">
                        <button onClick={() => setModalPkg(pkg)} className="p-1.5 rounded hover:bg-surface text-text-light">
                          <Pencil className="w-4 h-4" />
                        </button>
                        {pkg.active && (
                          <button onClick={() => deactivate.mutate(pkg.id)} disabled={deactivate.isPending}
                            className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.map((pkg) => (
              <div key={pkg.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <div>
                    <span className="font-semibold text-text">{pkg.name}</span>
                    <span className={cn("ml-2 px-2 py-0.5 rounded-full text-xs font-semibold",
                      pkg.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500")}>
                      {pkg.active ? "Active" : "Inactive"}
                    </span>
                  </div>
                  <div className="flex gap-1">
                    <button onClick={() => setModalPkg(pkg)} className="p-1.5 rounded-lg hover:bg-surface text-text-light">
                      <Pencil className="w-4 h-4" />
                    </button>
                    {pkg.active && (
                      <button onClick={() => deactivate.mutate(pkg.id)} disabled={deactivate.isPending}
                        className="p-1.5 rounded-lg hover:bg-red-50 text-red-400 disabled:opacity-50">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2 text-xs mt-3">
                  <div className="bg-surface rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Credits</div>
                    <div className="font-semibold text-text">{pkg.credits}</div>
                  </div>
                  <div className="bg-surface rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Price</div>
                    <div className="font-semibold text-text font-mono">₹{(pkg.price_paise / 100).toFixed(0)}</div>
                  </div>
                  <div className="bg-surface rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Order</div>
                    <div className="font-semibold text-text">{pkg.display_order}</div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {modalPkg !== null && (
        <PackageModal initial={modalPkg === "new" ? undefined : modalPkg} onClose={() => setModalPkg(null)} />
      )}
    </div>
  );
}
