import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Pencil, Plus, Trash2, X } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import {
  useAdminPackages,
  useCreatePackage,
  useDeletePackage,
  useUpdatePackage,
} from "../../hooks/admin/useAdminPackages";
import { useCan } from "../../hooks/useMyPermissions";
import type { AdminPackage, CreatePackagePayload } from "../../hooks/admin/useAdminPackages";
import { VALIDITY_UNITS, formatValidity, type ValidityUnit } from "../../lib/validity";
import { HlField, HlSelect, HlSwitch } from "../components/Controls";

const rupees = (paise: number) => `₹${(paise / 100).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;

function PackageSheet({ initial, onClose, onDelete }: { initial?: AdminPackage; onClose: () => void; onDelete?: () => void }) {
  const [error, setError] = useState("");
  const create = useCreatePackage();
  const update = useUpdatePackage();
  const isPending = create.isPending || update.isPending;
  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      credits: String(initial?.credits ?? 0),
      price_paise: String(initial?.price_paise ?? 0),
      currency: initial?.currency ?? "INR",
      display_order: String(initial?.display_order ?? 0),
      validity_value: String(initial?.validity_value ?? 0),
      validity_unit: (initial?.validity_unit ?? "DAYS") as ValidityUnit,
      active: initial?.active ?? true,
    },
    onSubmit: async ({ value }) => {
      setError("");
      const payload: CreatePackagePayload = {
        name: value.name,
        credits: parseInt(value.credits, 10) || 0,
        price_paise: parseInt(value.price_paise, 10) || 0,
        currency: value.currency,
        display_order: parseInt(value.display_order, 10) || 0,
        validity_value: parseInt(value.validity_value, 10) || 0,
        validity_unit: value.validity_unit,
      };
      try {
        if (initial) {
          await update.mutateAsync({ id: initial.id, payload: { ...payload, ...(value.active !== initial.active ? { active: value.active } : {}) } });
        } else {
          await create.mutateAsync(payload);
        }
        onClose();
      } catch {
        setError("Failed to save package.");
      }
    },
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent showCloseButton={false} className="hl-rsheet">
        <div className="hlx" style={{ height: "100%", gap: 0 }}>
          <header style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", padding: "24px 24px 18px", borderBottom: "1px solid var(--hair)" }}>
            <div>
              <DialogTitle asChild>
                <h2>{initial ? "Edit package" : "New package"}</h2>
              </DialogTitle>
              <DialogDescription asChild>
                <p className="muted" style={{ marginTop: 4 }}>
                  {initial ? `${initial.name} · ` : ""}changes apply to new purchases only
                </p>
              </DialogDescription>
            </div>
            <button className="icon-btn" type="button" aria-label="Close" onClick={onClose}>
              <X className="ico" />
            </button>
          </header>

          <form
            style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}
            onSubmit={(e) => {
              e.preventDefault();
              form.handleSubmit();
            }}
          >
            <div style={{ flex: 1, overflowY: "auto", padding: "22px 24px", display: "flex", flexDirection: "column", gap: 22 }}>
              <form.Field name="name">
                {(f) => (
                  <HlField label="Package name" htmlFor="pf-name" help="Shown to healers when they choose a plan.">
                    <input id="pf-name" className="input" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />
                  </HlField>
                )}
              </form.Field>

              <div className="row2">
                <form.Field name="credits">
                  {(f) => (
                    <HlField label="Credits" htmlFor="pf-credits">
                      <input id="pf-credits" className="input num" type="number" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />
                    </HlField>
                  )}
                </form.Field>
                <form.Field name="price_paise">
                  {(f) => (
                    <HlField label="Price (paise)" htmlFor="pf-price" help={<span className="num">= ₹{((parseInt(f.state.value, 10) || 0) / 100).toFixed(2)}</span>}>
                      <input id="pf-price" className="input num" type="number" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />
                    </HlField>
                  )}
                </form.Field>
              </div>

              <div>
                <label className="lbl" htmlFor="pf-valid">Plan validity</label>
                <div className="row2">
                  <form.Field name="validity_value">
                    {(f) => <input id="pf-valid" className="input num" type="number" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />}
                  </form.Field>
                  <form.Field name="validity_unit">
                    {(f) => (
                      <HlSelect label="Unit" value={f.state.value} onChange={(v) => f.handleChange(v as ValidityUnit)} options={VALIDITY_UNITS.map((u) => ({ value: u.value, label: u.label }))} />
                    )}
                  </form.Field>
                </div>
                <div className="help">Hours, days, months or years. Enter 0 for a plan that never expires. Unused paid credits are forfeited when it ends.</div>
              </div>

              <div className="row2">
                <form.Field name="currency">
                  {(f) => (
                    <HlField label="Currency" htmlFor="pf-cur">
                      <input id="pf-cur" className="input" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />
                    </HlField>
                  )}
                </form.Field>
                <form.Field name="display_order">
                  {(f) => (
                    <HlField label="Display order" htmlFor="pf-order" help="Higher plans are the upgrades.">
                      <input id="pf-order" className="input num" type="number" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />
                    </HlField>
                  )}
                </form.Field>
              </div>

              {initial && (
                <form.Field name="active">
                  {(f) => (
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, padding: "14px 16px", borderRadius: 12, background: "var(--primary-soft)" }}>
                      <div>
                        <div style={{ fontWeight: 600 }}>Offer this package</div>
                        <div className="small muted">Turn off to hide it from healers without deleting it.</div>
                      </div>
                      <HlSwitch label="Offer this package" checked={f.state.value} onChange={(v) => f.handleChange(v)} />
                    </div>
                  )}
                </form.Field>
              )}

              {initial && onDelete && (
                <div style={{ borderTop: "1px solid var(--hair)", paddingTop: 16, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16 }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>Delete package</div>
                    <div className="small muted" style={{ maxWidth: "36ch" }}>Permanent. Blocked while any account is on this plan; disable it instead.</div>
                  </div>
                  <button className="btn btn-danger" type="button" onClick={onDelete}>Delete</button>
                </div>
              )}
              {error && <div className="err">{error}</div>}
            </div>

            <footer style={{ display: "flex", justifyContent: "flex-end", gap: 10, padding: "16px 24px", borderTop: "1px solid var(--hair)" }}>
              <button className="btn btn-secondary" type="button" onClick={onClose}>Cancel</button>
              <button className="btn btn-primary" type="submit" disabled={isPending}>
                {isPending ? "Saving…" : initial ? "Save changes" : "Create package"}
              </button>
            </footer>
          </form>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** New-view Packages: same data and actions as the classic page, laid out as in the approved mockup. */
export default function NewPackagesPage() {
  const { data, isLoading, isError } = useAdminPackages();
  const remove = useDeletePackage();
  const update = useUpdatePackage();
  const [sheet, setSheet] = useState<AdminPackage | null | "new">(null);
  const [deletePkg, setDeletePkg] = useState<AdminPackage | null>(null);
  const can = useCan();
  const canCreate = can("PACKAGES", "CREATE");
  const canUpdate = can("PACKAGES", "UPDATE");
  const canDelete = can("PACKAGES", "DELETE");

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Credit packages</h1>
          <p className="muted" style={{ marginTop: 6 }}>The plans healers can buy. Changes apply to new purchases only.</p>
        </div>
        {canCreate && (
          <button className="btn btn-primary" type="button" onClick={() => setSheet("new")}>
            <Plus className="ico" /> New package
          </button>
        )}
      </div>

      {isLoading && <p className="muted">Loading…</p>}
      {isError && <p className="err">Failed to load packages.</p>}

      {data && (
        <>
          <div className="surface t-wrap">
            <table className="t">
              <thead>
                <tr>
                  <th>Package</th>
                  <th className="r">Credits</th>
                  <th className="r">Price</th>
                  <th>Validity</th>
                  <th className="r">Order</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.length === 0 && (
                  <tr><td colSpan={7} className="muted" style={{ textAlign: "center" }}>No packages found.</td></tr>
                )}
                {data.map((p) => (
                  <tr key={p.id}>
                    <td data-label="Package"><span className="name">{p.name}</span></td>
                    <td className="r" data-label="Credits"><span className="num">{p.credits.toLocaleString("en-IN")}</span></td>
                    <td className="r" data-label="Price"><span className="num">{rupees(p.price_paise)}</span></td>
                    <td data-label="Validity"><span className="muted">{formatValidity(p.validity_value, p.validity_unit)}</span></td>
                    <td className="r" data-label="Order"><span className="num muted">{p.display_order}</span></td>
                    <td data-label="Status">
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        {canUpdate && (
                          <HlSwitch
                            label={`${p.active ? "Disable" : "Enable"} ${p.name}`}
                            checked={p.active}
                            disabled={update.isPending}
                            onChange={(active) => update.mutate({ id: p.id, payload: { active } })}
                          />
                        )}
                        <span className={`badge ${p.active ? "ok" : "off"}`}>{p.active ? "Active" : "Disabled"}</span>
                      </div>
                    </td>
                    <td className="r" data-label="">
                      <div style={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
                        {canUpdate && (
                          <button className="icon-btn" type="button" aria-label={`Edit ${p.name}`} onClick={() => setSheet(p)}>
                            <Pencil className="ico" />
                          </button>
                        )}
                        {canDelete && (
                          <button className="icon-btn danger" type="button" aria-label={`Delete ${p.name}`} disabled={remove.isPending} onClick={() => setDeletePkg(p)}>
                            <Trash2 className="ico" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="small ongr" style={{ padding: "0 4px", maxWidth: "70ch" }}>
            Disable a package to stop offering it without losing it; delete removes it permanently and is blocked while any account is on that plan.
          </p>
        </>
      )}

      {sheet !== null && (
        <PackageSheet
          initial={sheet === "new" ? undefined : sheet}
          onClose={() => setSheet(null)}
          onDelete={sheet === "new" || !canDelete ? undefined : () => { setDeletePkg(sheet); setSheet(null); }}
        />
      )}

      <ConfirmDialog
        open={deletePkg !== null}
        onOpenChange={(open) => !open && setDeletePkg(null)}
        title="Delete package?"
        description={deletePkg ? `"${deletePkg.name}" will be removed permanently. To only stop offering it, use the switch to disable it instead.` : undefined}
        confirmLabel="Delete permanently"
        destructive
        loading={remove.isPending}
        onConfirm={() => {
          if (deletePkg) remove.mutate(deletePkg.id, { onSettled: () => setDeletePkg(null) });
        }}
      />
    </div>
  );
}
