import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, ArrowRight } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Patient } from "@/context/PatientContext";
import type { HPUTable } from "@/interfaces";
import { useProtocolColors, useProtocolUnderstanding } from "@/hooks/useProtocolUnderstanding";
import { useCaseClosure } from "@/pages/Closure";
import ChakraDetail from "../closure/ChakraDetail";
import WrapUp from "../closure/WrapUp";

const hasReason = (row: HPUTable) => !!row.reason?.trim();

/**
 * New-view close-case page: a list of the scanned chakras (plus a last "Wrap up" step) on the left,
 * the selected one on the right. "Save & next" saves the chakras that have a reason (the server
 * refuses one without); "Close case" saves them all and closes the case.
 */
export default function CloseCase() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { healingCaseId = "" } = useParams<{ healingCaseId: string }>();
  const patient = (useLocation().state as { patient?: Patient } | null)?.patient;
  const { rows, ready, loading, updateRow, save } = useProtocolUnderstanding(healingCaseId);
  const colors = useProtocolColors();
  const closure = useCaseClosure(healingCaseId, () => {
    queryClient.invalidateQueries({ queryKey: ["patients"] });
    navigate(-1);
  });
  // rows.length is the wrap-up step. On phones the list shows until an item is opened.
  const [selected, setSelected] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);

  const done = rows.filter(hasReason).length;
  const allDone = ready && done === rows.length;
  const busy = save.isPending || closure.closeMutation.isPending;
  const isWrapUp = selected >= rows.length;
  const row = rows[selected];
  const nameOf = (i: number) => (i >= rows.length ? "Wrap up" : rows[i].chakra);

  const open = (i: number) => {
    setSelected(i);
    setDetailOpen(true);
  };
  const saveAndNext = () => {
    const reasoned = rows.filter(hasReason);
    if (reasoned.length === 0) return open(selected + 1);
    save.mutate(reasoned, { onSuccess: () => open(selected + 1) });
  };
  const closeCase = () => save.mutate(undefined, { onSuccess: () => closure.closeMutation.mutate() });

  const closeButton = (
    <Button type="button" onClick={closeCase} disabled={!allDone || busy}>
      {closure.closeMutation.isPending ? "Closing..." : "Close case"}
    </Button>
  );

  return (
    <div className="hlx mx-auto max-w-5xl pb-10">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="space-y-1">
          <Button type="button" variant="ghost" onClick={() => navigate(-1)} className="h-auto px-0 gap-1.5 text-sm">
            <ArrowLeft className="w-4 h-4" />
            Back{patient ? ` · ${patient.firstName} ${patient.lastName}` : ""}
          </Button>
          <h1>Close case</h1>
          <p className="muted small">Say why you worked on each chakra and the colours you used. Then wrap up.</p>
        </div>
        {closeButton}
      </div>

      <div className="hl-cc mt-4">
        <nav aria-label="Close case steps" className={`surface card hl-cc-list ${detailOpen ? "max-md:hidden" : ""}`}>
          <div className="hl-cc-list-h">
            <strong>Chakras</strong>
            <span className="muted small">{loading ? "Loading..." : `${done} of ${rows.length} done`}</span>
          </div>
          <div
            className="hl-prog"
            role="progressbar"
            aria-label="Chakras done"
            aria-valuemin={0}
            aria-valuemax={rows.length}
            aria-valuenow={done}
          >
            <i style={{ width: rows.length ? `${(done / rows.length) * 100}%` : "100%" }} />
          </div>
          {[...rows, null].map((r, i) => {
            const ok = r ? hasReason(r) : false;
            return (
              <button
                key={r?.chakra ?? "wrap-up"}
                type="button"
                className={`hl-cc-row${r ? "" : " hl-cc-last"}`}
                aria-current={selected === i ? "step" : undefined}
                onClick={() => open(i)}
              >
                <span className="min-w-0 flex-1">
                  <span className="hl-cc-name">{r ? r.chakra : "Wrap up"}</span>
                  <span className="hl-cc-sum">{r ? (ok ? r.reason : "Needs a reason") : "Protocols · comments · continued?"}</span>
                </span>
                <span className={`hl-badge ${r ? (ok ? "hl-badge-ok" : "hl-badge-warn") : selected === i ? "hl-badge-warn" : "hl-badge-off"}`}>
                  {r ? (ok ? "✓ Done" : "To do") : selected === i ? "Now" : "Last"}
                </span>
              </button>
            );
          })}
        </nav>

        <section className={`surface card ${detailOpen ? "" : "max-md:hidden"}`}>
          <button type="button" className="hl-link mb-3 md:hidden" onClick={() => setDetailOpen(false)}>
            ← Back to list
          </button>
          {ready && (
            <>
              <div className="card-h">
                <h2>{nameOf(selected)}</h2>
                <span className="muted small">{isWrapUp ? "Last step" : `Chakra ${selected + 1} of ${rows.length}`}</span>
              </div>
              {isWrapUp ? (
                <WrapUp closure={closure} />
              ) : (
                <ChakraDetail
                  key={row.chakra}
                  row={row}
                  colors={colors}
                  onChange={(field, value) => updateRow(selected, field, value)}
                />
              )}
              {isWrapUp && !allDone && (
                <p role="note" className="muted small mt-3">Add why you worked on each chakra to close the case.</p>
              )}
              <div className="hl-cc-foot">
                {selected > 0 ? (
                  <Button type="button" variant="outline" onClick={() => open(selected - 1)} disabled={busy}>
                    <ArrowLeft className="w-4 h-4" aria-hidden="true" />
                    {nameOf(selected - 1)}
                  </Button>
                ) : (
                  <span />
                )}
                {isWrapUp ? (
                  closeButton
                ) : (
                  <Button type="button" onClick={saveAndNext} disabled={busy}>
                    {save.isPending ? "Saving..." : `Save & next: ${nameOf(selected + 1)}`}
                    <ArrowRight className="w-4 h-4" aria-hidden="true" />
                  </Button>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
