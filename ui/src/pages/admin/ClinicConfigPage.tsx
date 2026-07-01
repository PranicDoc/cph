import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import {
  useFallbackPolicy,
  useUpsertFallbackPolicy,
  useAdminAdjust,
  useAdminPaygToggle,
} from "../../hooks/admin/useAdminClinicConfig";
import { useClinicHealers, useToggleHealerActive } from "../../hooks/admin/useAdminHealers";

export default function ClinicConfigPage() {
  const { user } = useAuth();
  const clinicId = user?.clinicId ?? "";

  const { data: fallback, isLoading: fallbackLoading } = useFallbackPolicy(clinicId || undefined);
  const upsertFallback = useUpsertFallbackPolicy(clinicId);
  const adjust = useAdminAdjust();
  const paygToggle = useAdminPaygToggle();

  const [adjustOwnerId, setAdjustOwnerId] = useState("");
  const [adjustOwnerType, setAdjustOwnerType] = useState("CLINIC");
  const [adjustAmount, setAdjustAmount] = useState("");
  const [adjustNotes, setAdjustNotes] = useState("");
  const [adjustMsg, setAdjustMsg] = useState("");

  const [paygOwnerId, setPaygOwnerId] = useState("");
  const [paygEnabled, setPaygEnabled] = useState(false);
  const [paygBundle, setPaygBundle] = useState("");
  const [paygMsg, setPaygMsg] = useState("");

  const handleAdjust = async (e: React.FormEvent) => {
    e.preventDefault();
    setAdjustMsg("");
    try {
      const res = await adjust.mutateAsync({
        ownerType: adjustOwnerType,
        ownerId: adjustOwnerId,
        amount: parseInt(adjustAmount, 10),
        notes: adjustNotes || undefined,
      });
      setAdjustMsg(`Done. New balance: ${res.new_balance}`);
    } catch {
      setAdjustMsg("Failed to apply adjustment.");
    }
  };

  const handlePaygToggle = async (e: React.FormEvent) => {
    e.preventDefault();
    setPaygMsg("");
    try {
      await paygToggle.mutateAsync({
        ownerType: "HEALER",
        ownerId: paygOwnerId,
        paygEnabled,
        bundleCredits: paygBundle ? parseInt(paygBundle, 10) : undefined,
      });
      setPaygMsg("PAYG settings updated.");
    } catch {
      setPaygMsg("Failed to update PAYG settings.");
    }
  };

  const { data: healers = [], isLoading: healersLoading } = useClinicHealers();
  const toggleHealer = useToggleHealerActive();

  return (
    <div className="space-y-4 md:space-y-8 max-w-2xl">
      <h1 className="text-xl md:text-2xl font-bold text-text">Clinic Config</h1>

      {/* Fallback Policy */}
      <section className="border border-border rounded-xl p-4 md:p-6 space-y-3 md:space-y-4">
        <h2 className="text-base md:text-lg font-semibold text-text">Org Fallback Policy</h2>
        <p className="text-sm text-text-light">
          When enabled, healers can use their personal credit balance if the clinic's pool is exhausted.
        </p>
        {fallbackLoading ? (
          <p className="text-text-light text-sm">Loading…</p>
        ) : (
          <div className="flex items-center gap-3">
            <div
              onClick={() => !upsertFallback.isPending && upsertFallback.mutate(!fallback?.allow_individual_fallback)}
              className={`w-12 h-6 rounded-full transition-colors cursor-pointer shrink-0 ${
                fallback?.allow_individual_fallback ? "bg-primary" : "bg-gray-300"
              }`}
            >
              <div className={`w-5 h-5 m-0.5 bg-white rounded-full shadow transition-transform ${
                fallback?.allow_individual_fallback ? "translate-x-6" : "translate-x-0"
              }`} />
            </div>
            <span className="text-sm text-text font-medium">
              {fallback?.allow_individual_fallback ? "Enabled" : "Disabled"}
            </span>
            {upsertFallback.isPending && <span className="text-xs text-text-light">Saving…</span>}
          </div>
        )}
      </section>

      {/* Manual Credit Adjustment */}
      <section className="border border-border rounded-xl p-4 md:p-6 space-y-3 md:space-y-4">
        <h2 className="text-base md:text-lg font-semibold text-text">Manual Credit Adjustment</h2>
        <p className="text-sm text-text-light">
          Positive adds to paid balance; negative subtracts. Free balance is protected.
        </p>
        <form onSubmit={handleAdjust} className="space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-text-light mb-1">Owner Type</label>
              <select value={adjustOwnerType} onChange={(e) => setAdjustOwnerType(e.target.value)}
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm bg-surface">
                <option value="CLINIC">Clinic</option>
                <option value="HEALER">Healer</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-text-light mb-1">Owner ID (UUID)</label>
              <input type="text" value={adjustOwnerId} onChange={(e) => setAdjustOwnerId(e.target.value)}
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm font-mono" required />
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-text-light mb-1">Amount (signed)</label>
              <input type="number" value={adjustAmount} onChange={(e) => setAdjustAmount(e.target.value)}
                placeholder="+50 or -10"
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" required />
            </div>
            <div>
              <label className="block text-xs text-text-light mb-1">Notes (optional)</label>
              <input type="text" value={adjustNotes} onChange={(e) => setAdjustNotes(e.target.value)}
                className="w-full border border-border rounded-lg px-3 py-2.5 text-sm" />
            </div>
          </div>
          <button type="submit" disabled={adjust.isPending}
            className="w-full sm:w-auto px-4 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60">
            {adjust.isPending ? "Applying…" : "Apply Adjustment"}
          </button>
          {adjustMsg && (
            <p className={`text-sm ${adjustMsg.startsWith("Failed") ? "text-red-500" : "text-green-600"}`}>
              {adjustMsg}
            </p>
          )}
        </form>
      </section>

      {/* Healers */}
      <section className="border border-border rounded-xl p-4 md:p-6 space-y-3 md:space-y-4">
        <h2 className="text-base md:text-lg font-semibold text-text">Healers</h2>
        <p className="text-sm text-text-light">Activate or deactivate healer accounts in your clinic.</p>
        {healersLoading ? (
          <p className="text-text-light text-sm">Loading…</p>
        ) : healers.length === 0 ? (
          <p className="text-text-light text-sm">No healers found in your clinic.</p>
        ) : (
          <div className="divide-y divide-border rounded-lg border border-border overflow-hidden">
            {healers.map((h) => (
              <div key={h.id} className="flex items-center justify-between px-4 py-3">
                <div>
                  <p className="text-sm font-medium text-text">{h.fullName}</p>
                  <p className="text-xs text-text-light">{h.email} · {h.role}</p>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${h.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                    {h.active ? "Active" : "Inactive"}
                  </span>
                  <button
                    onClick={() => toggleHealer.mutate({ id: h.id, active: !h.active })}
                    disabled={toggleHealer.isPending}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors disabled:opacity-50 ${
                      h.active
                        ? "text-red-600 border border-red-200 hover:bg-red-50"
                        : "text-green-600 border border-green-200 hover:bg-green-50"
                    }`}
                  >
                    {h.active ? "Deactivate" : "Activate"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* PAYG Settings */}
      <section className="border border-border rounded-xl p-4 md:p-6 space-y-3 md:space-y-4">
        <h2 className="text-base md:text-lg font-semibold text-text">PAYG Settings (Healer Account)</h2>
        <p className="text-sm text-text-light">Toggle Pay-As-You-Go auto top-up for a specific healer account.</p>
        <form onSubmit={handlePaygToggle} className="space-y-3">
          <div>
            <label className="block text-xs text-text-light mb-1">Healer ID (UUID)</label>
            <input type="text" value={paygOwnerId} onChange={(e) => setPaygOwnerId(e.target.value)}
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              className="w-full border border-border rounded-lg px-3 py-2.5 text-sm font-mono" required />
          </div>
          <div className="flex flex-wrap items-end gap-4">
            <label className="flex items-center gap-2 text-sm text-text cursor-pointer">
              <input type="checkbox" checked={paygEnabled} onChange={(e) => setPaygEnabled(e.target.checked)}
                className="w-4 h-4 rounded" />
              Enable PAYG
            </label>
            <div>
              <label className="block text-xs text-text-light mb-1">Bundle Credits</label>
              <input type="number" value={paygBundle} onChange={(e) => setPaygBundle(e.target.value)}
                placeholder="default: 1" min="1"
                className="border border-border rounded-lg px-3 py-2.5 text-sm w-32" />
            </div>
          </div>
          <button type="submit" disabled={paygToggle.isPending}
            className="w-full sm:w-auto px-4 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60">
            {paygToggle.isPending ? "Saving…" : "Save PAYG Settings"}
          </button>
          {paygMsg && (
            <p className={`text-sm ${paygMsg.startsWith("Failed") ? "text-red-500" : "text-green-600"}`}>
              {paygMsg}
            </p>
          )}
        </form>
      </section>
    </div>
  );
}
