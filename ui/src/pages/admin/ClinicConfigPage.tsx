import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useAuth } from "../../context/AuthContext";
import {
  useFallbackPolicy,
  useUpsertFallbackPolicy,
  useAdminAdjust,
  useAdminPaygToggle,
} from "../../hooks/admin/useAdminClinicConfig";
import { useClinicHealers, useToggleHealerActive } from "../../hooks/admin/useAdminHealers";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  CheckboxField,
  SelectField,
  TextField,
} from "@/components/form/Field";
import { cn } from "@/lib/utils";

export default function ClinicConfigPage() {
  const { user } = useAuth();
  const clinicId = user?.clinicId ?? "";

  const { data: fallback, isLoading: fallbackLoading } = useFallbackPolicy(clinicId || undefined);
  const upsertFallback = useUpsertFallbackPolicy(clinicId);
  const adjust = useAdminAdjust();
  const paygToggle = useAdminPaygToggle();

  const [adjustMsg, setAdjustMsg] = useState("");
  const [paygMsg, setPaygMsg] = useState("");

  const adjustForm = useForm({
    defaultValues: {
      ownerType: "CLINIC",
      ownerId: "",
      amount: "",
      notes: "",
    },
    onSubmit: async ({ value }) => {
      setAdjustMsg("");
      try {
        const res = await adjust.mutateAsync({
          ownerType: value.ownerType,
          ownerId: value.ownerId,
          amount: parseInt(value.amount, 10),
          notes: value.notes || undefined,
        });
        setAdjustMsg(`Done. New balance: ${res.new_balance}`);
      } catch {
        setAdjustMsg("Failed to apply adjustment.");
      }
    },
  });

  const paygForm = useForm({
    defaultValues: {
      ownerId: "",
      enabled: false,
      bundleCredits: "",
    },
    onSubmit: async ({ value }) => {
      setPaygMsg("");
      try {
        await paygToggle.mutateAsync({
          ownerType: "HEALER",
          ownerId: value.ownerId,
          paygEnabled: value.enabled,
          bundleCredits: value.bundleCredits
            ? parseInt(value.bundleCredits, 10)
            : undefined,
        });
        setPaygMsg("PAYG settings updated.");
      } catch {
        setPaygMsg("Failed to update PAYG settings.");
      }
    },
  });

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
            <Switch
              checked={Boolean(fallback?.allow_individual_fallback)}
              disabled={upsertFallback.isPending}
              onCheckedChange={() =>
                upsertFallback.mutate(!fallback?.allow_individual_fallback)
              }
              className="h-6 w-12 data-unchecked:bg-gray-300 [&_[data-slot=switch-thumb]]:size-5"
            />
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
        <form
          onSubmit={(e) => {
            e.preventDefault();
            adjustForm.handleSubmit();
          }}
          className="space-y-3"
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">Owner Type</Label>
              <adjustForm.Field
                name="ownerType"
                children={(field) => (
                  <SelectField
                    field={field}
                    options={[
                      { label: "Clinic", value: "CLINIC" },
                      { label: "Healer", value: "HEALER" },
                    ]}
                    className="border px-3 py-2.5 bg-muted/50"
                  />
                )}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">Owner ID (UUID)</Label>
              <adjustForm.Field
                name="ownerId"
                children={(field) => (
                  <TextField
                    field={field}
                    placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    className="border px-3 py-2.5 font-mono"
                  />
                )}
              />
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">Amount (signed)</Label>
              <adjustForm.Field
                name="amount"
                children={(field) => (
                  <TextField
                    field={field}
                    type="number"
                    placeholder="+50 or -10"
                    className="border px-3 py-2.5"
                  />
                )}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">Notes (optional)</Label>
              <adjustForm.Field
                name="notes"
                children={(field) => (
                  <TextField field={field} className="border px-3 py-2.5" />
                )}
              />
            </div>
          </div>
          <Button
            type="submit"
            disabled={adjust.isPending}
            className="w-full sm:w-auto px-4 py-2.5 h-auto"
          >
            {adjust.isPending ? "Applying…" : "Apply Adjustment"}
          </Button>
          {adjustMsg && (
            <p className={cn("text-sm", adjustMsg.startsWith("Failed") ? "text-red-500" : "text-green-600")}>
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
                  <StatusBadge active={h.active} />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => toggleHealer.mutate({ id: h.id, active: !h.active })}
                    disabled={toggleHealer.isPending}
                    className={cn(
                      "px-3 text-xs h-auto py-1.5",
                      h.active
                        ? "text-red-600 border-red-200 hover:bg-red-50 hover:text-red-600"
                        : "text-green-600 border-green-200 hover:bg-green-50 hover:text-green-600"
                    )}
                  >
                    {h.active ? "Deactivate" : "Activate"}
                  </Button>
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
        <form
          onSubmit={(e) => {
            e.preventDefault();
            paygForm.handleSubmit();
          }}
          className="space-y-3"
        >
          <div>
            <Label className="block text-xs text-text-light mb-1">Healer ID (UUID)</Label>
            <paygForm.Field
              name="ownerId"
              children={(field) => (
                <TextField
                  field={field}
                  placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                  className="border px-3 py-2.5 font-mono"
                />
              )}
            />
          </div>
          <div className="flex flex-wrap items-end gap-4">
            <paygForm.Field
              name="enabled"
              children={(field) => (
                <CheckboxField
                  field={field}
                  label="Enable PAYG"
                  wrapperClassName="text-sm text-text"
                />
              )}
            />
            <div>
              <Label className="block text-xs text-text-light mb-1">Bundle Credits</Label>
              <paygForm.Field
                name="bundleCredits"
                children={(field) => (
                  <TextField
                    field={field}
                    type="number"
                    placeholder="default: 1"
                    className="border px-3 py-2.5 w-32"
                  />
                )}
              />
            </div>
          </div>
          <Button
            type="submit"
            disabled={paygToggle.isPending}
            className="w-full sm:w-auto px-4 py-2.5 h-auto"
          >
            {paygToggle.isPending ? "Saving…" : "Save PAYG Settings"}
          </Button>
          {paygMsg && (
            <p className={cn("text-sm", paygMsg.startsWith("Failed") ? "text-red-500" : "text-green-600")}>
              {paygMsg}
            </p>
          )}
        </form>
      </section>
    </div>
  );
}
