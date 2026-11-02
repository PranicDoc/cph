import { useMemo, useState } from "react";
import { X } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { useAdminHealers } from "../../hooks/admin/useAdminHealers";
import { useGrantCustomPlan } from "../../hooks/admin/useAdminAccounts";
import { VALIDITY_UNITS, type ValidityUnit } from "../../lib/validity";
import { extractDetail } from "../../lib/utils";
import { HlField, HlSelect } from "../components/Controls";

/**
 * Super-admin dialog: give a healer a custom plan. Replaces whatever plan they are on, resets their
 * paid credits to the number entered and sets the expiry from value + unit (0 = no expiry date).
 * Only rendered for SUPADM; the backend enforces it regardless.
 */
export default function CustomPlanSheet({ onClose, initialHealerId }: { onClose: () => void; initialHealerId?: string }) {
  const { data: healers, isLoading } = useAdminHealers(true);
  const grant = useGrantCustomPlan();
  const [healerId, setHealerId] = useState(initialHealerId ?? "");
  const [planTag, setPlanTag] = useState("CUSTOM");
  const [credits, setCredits] = useState("");
  const [validityValue, setValidityValue] = useState("30");
  const [validityUnit, setValidityUnit] = useState<ValidityUnit>("DAYS");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");

  const options = useMemo(
    () => [
      { value: "", label: isLoading ? "Loading healers…" : "Select a healer" },
      ...(healers ?? [])
        .filter((h) => h.active)
        .sort((a, b) => a.fullName.localeCompare(b.fullName))
        .map((h) => ({ value: h.id, label: `${h.fullName} · ${h.email}` })),
    ],
    [healers, isLoading]
  );

  const creditsNum = parseInt(credits, 10);
  const validityNum = parseInt(validityValue, 10) || 0;
  const tag = planTag.trim().toUpperCase() || "CUSTOM";

  const submit = async () => {
    setError("");
    if (!healerId) return setError("Choose a healer.");
    if (!creditsNum || creditsNum < 1) return setError("Enter the number of credits (at least 1).");
    if (validityNum < 0) return setError("Validity cannot be negative.");
    try {
      await grant.mutateAsync({
        healerId,
        payload: {
          plan_tag: tag,
          credits: creditsNum,
          validity_value: validityNum,
          ...(validityNum > 0 ? { validity_unit: validityUnit } : {}),
          ...(notes.trim() ? { notes: notes.trim() } : {}),
        },
      });
      onClose();
    } catch (e) {
      setError(extractDetail(e) ?? "Could not set the plan.");
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent showCloseButton={false} className="hl-rsheet">
        <div className="hlx" style={{ height: "100%", gap: 0 }}>
          <header style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", padding: "24px 24px 18px", borderBottom: "1px solid var(--hair)" }}>
            <div>
              <DialogTitle asChild>
                <h2>Set custom plan</h2>
              </DialogTitle>
              <DialogDescription asChild>
                <p className="muted" style={{ marginTop: 4 }}>Replaces the healer's current plan with this one.</p>
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
              void submit();
            }}
          >
            <div style={{ flex: 1, overflowY: "auto", padding: "22px 24px", display: "flex", flexDirection: "column", gap: 22 }}>
              <HlField label="Healer" htmlFor="cp-healer">
                <HlSelect id="cp-healer" label="Healer" value={healerId} onChange={setHealerId} options={options} />
              </HlField>

              <HlField label="Plan tag" htmlFor="cp-tag" help="Shown as the account's plan. Leave as CUSTOM or use your own label, e.g. PILOT. Can't be FREE or a package name.">
                <input id="cp-tag" className="input" maxLength={50} value={planTag} onChange={(e) => setPlanTag(e.target.value)} />
              </HlField>

              <HlField label="Credits" htmlFor="cp-credits" help="The healer's paid credits are reset to this number; unused credits from the old plan are forfeited. Free credits are kept.">
                <input id="cp-credits" className="input num" type="number" min={1} value={credits} onChange={(e) => setCredits(e.target.value)} />
              </HlField>

              <div>
                <label className="lbl" htmlFor="cp-valid">Plan validity</label>
                <div className="row2">
                  <input id="cp-valid" className="input num" type="number" min={0} value={validityValue} onChange={(e) => setValidityValue(e.target.value)} />
                  <HlSelect label="Unit" value={validityUnit} onChange={(v) => setValidityUnit(v as ValidityUnit)} options={VALIDITY_UNITS.map((u) => ({ value: u.value, label: u.label }))} />
                </div>
                <div className="help">Counted from now. Enter 0 for no expiry date; the plan then lasts until the credits are used up.</div>
              </div>

              <HlField label="Note (optional)" htmlFor="cp-notes" help="Kept in the credit history.">
                <input id="cp-notes" className="input" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </HlField>

              {error && <div className="err">{error}</div>}
            </div>

            <footer style={{ display: "flex", justifyContent: "flex-end", gap: 10, padding: "16px 24px", borderTop: "1px solid var(--hair)" }}>
              <button className="btn btn-secondary" type="button" onClick={onClose}>Cancel</button>
              <button className="btn btn-primary" type="submit" disabled={grant.isPending}>
                {grant.isPending ? "Saving…" : `Set ${tag} plan`}
              </button>
            </footer>
          </form>
        </div>
      </DialogContent>
    </Dialog>
  );
}
