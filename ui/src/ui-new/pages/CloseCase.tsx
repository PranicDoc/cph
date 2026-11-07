import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Patient } from "@/context/PatientContext";
import type { HPUTable } from "@/interfaces";
import { useProtocolColors, useProtocolUnderstanding } from "@/hooks/useProtocolUnderstanding";
import { useCaseClosure } from "@/hooks/useCaseClosure";
import { useUsualPicks } from "@/hooks/useUsualPicks";
import ChakraDetail from "../closure/ChakraDetail";
import WrapUp from "../closure/WrapUp";
import { PickerHost } from "../closure/pickerHost";

const hasReason = (row: HPUTable) => !!row.reason?.trim();

/**
 * New-view close-case page: a list of the scanned chakras (plus a last "Wrap up" step) on the left,
 * the selected one on the right; on a phone the list and the selected item are separate screens.
 * "Save and next" saves the chakras that have a reason (the server refuses one without, and a save
 * replaces the whole set, so it waits while a chakra saved before lacks one); "Close case" saves
 * them all and closes the case.
 */
export default function CloseCase() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { healingCaseId = "" } = useParams<{ healingCaseId: string }>();
  const patient = (useLocation().state as { patient?: Patient } | null)?.patient;
  const { rows, ready, loading, savedChakras, updateRow, save } = useProtocolUnderstanding(healingCaseId);
  const colors = useProtocolColors();
  const picksFor = useUsualPicks(healingCaseId);
  const closure = useCaseClosure(healingCaseId, () => {
    queryClient.invalidateQueries({ queryKey: ["patients"] });
    navigate(-1);
  });
  // rows.length is the wrap-up step. On phones the list shows until an item is opened.
  const [selected, setSelected] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [panel, setPanel] = useState<HTMLElement | null>(null);
  // Where focus goes after the selected item or the phone view changes.
  const focusTo = useRef<"heading" | "row" | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const rowRefs = useRef<(HTMLButtonElement | null)[]>([]);
  useEffect(() => {
    const target = focusTo.current;
    focusTo.current = null;
    if (target === "heading") headingRef.current?.focus();
    else if (target === "row") rowRefs.current[selected]?.focus();
  }, [selected, detailOpen]);

  const done = rows.filter(hasReason).length;
  const allDone = ready && done === rows.length;
  // Saving without these would erase what was saved for them, so Save and next waits (Close case
  // saves everything once every chakra has a reason).
  const unsaved = rows.filter((r) => savedChakras.includes(r.chakra) && !hasReason(r));
  const busy = save.isPending || closure.closeMutation.isPending;
  const isWrapUp = selected >= rows.length;
  const row = rows[selected];
  const nameOf = (i: number) => (i >= rows.length ? "Wrap up" : rows[i].chakra);

  const open = (i: number) => {
    focusTo.current = "heading";
    setSelected(i);
    setDetailOpen(true);
  };
  const openNext = () => {
    focusTo.current = "heading";
    setSelected((s) => s + 1);
    setDetailOpen(true);
  };
  const backToList = () => {
    focusTo.current = "row";
    setDetailOpen(false);
  };
  const saveAndNext = () => {
    const reasoned = rows.filter(hasReason);
    if (reasoned.length === 0 || unsaved.length > 0) return openNext();
    save.mutate(reasoned, { onSuccess: openNext });
  };
  const closeCase = () =>
    rows.length === 0
      ? closure.closeMutation.mutate()
      : save.mutate(undefined, { onSuccess: () => closure.closeMutation.mutate() });

  const closeButton = (className: string) => (
    <Button
      type="button"
      className={className}
      onClick={closeCase}
      disabled={!allDone || !closure.loaded || busy}
    >
      {closure.closeMutation.isPending ? "Closing..." : "Close case"}
    </Button>
  );
  const status = (r: HPUTable | null) => {
    const [tone, label] = r ? (hasReason(r) ? ["ok", "Done"] : ["warn", "To do"]) : ["off", "Last"];
    return <span className={`hl-badge hl-badge-${tone}`}>{label}</span>;
  };
  const listRow = (r: HPUTable | null, i: number) => (
    <button
      key={r?.chakra ?? "wrap-up"}
      type="button"
      className={`hl-cc-row${r ? "" : " hl-cc-last"}`}
      aria-current={selected === i ? "step" : undefined}
      disabled={busy}
      ref={(el) => {
        rowRefs.current[i] = el;
      }}
      onClick={() => open(i)}
    >
      <span className="min-w-0 flex-1">
        <span className="hl-cc-name">{r ? r.chakra : "Wrap up"}</span>
        <span className="hl-cc-sum">{r ? (hasReason(r) ? r.reason : "Needs a reason") : "Protocols, comments, continued?"}</span>
      </span>
      <span className="flex items-center gap-2">
        {status(r)}
        <ChevronRight className="hl-cc-chev md:hidden" aria-hidden="true" />
      </span>
    </button>
  );

  return (
    <div className="hlx hl-ccp mx-auto max-w-6xl" data-view={detailOpen ? "detail" : "list"}>
      <header className="hl-cc-head">
        <div className="flex flex-col gap-1">
          <button type="button" className="hl-cc-back" onClick={() => navigate(-1)}>
            Back to session
          </button>
          <h1>{patient ? `Close case for ${patient.firstName} ${patient.lastName}` : "Close case"}</h1>
          <p className="muted">Say why you worked on each chakra and the colours you used. Then wrap up.</p>
          {closure.loadFailed && (
            <p role="alert" className="text-danger small">
              Couldn't load this case's closure details. Reload the page to close the case.
            </p>
          )}
        </div>
        <div className="max-md:hidden">{closeButton("hl-btn44")}</div>
      </header>

      <div className="hl-cc">
        <nav aria-label="Close case steps" className="hl-cc-list">
          <div className="hl-cc-list-h">
            <strong className="max-md:hidden">Chakras</strong>
            <span className="muted small">{loading ? "Loading..." : `${done} of ${rows.length} done`}</span>
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
          </div>
          <div className="hl-cc-rows">{rows.map((r, i) => listRow(r, i))}</div>
          {listRow(null, rows.length)}
        </nav>

        <section ref={setPanel} className="hl-cc-panel">
          {ready && (
            <>
              <div className="hl-cc-panel-h">
                <button type="button" className="hl-cc-all md:hidden" onClick={backToList}>
                  <ChevronLeft className="w-4 h-4" aria-hidden="true" />
                  All chakras
                </button>
                <div className="flex items-baseline gap-2 max-md:justify-between">
                  <h2 ref={headingRef} tabIndex={-1}>
                    {nameOf(selected)}
                  </h2>
                  {isWrapUp ? (
                    <span className={`small ${allDone ? "hl-ok-text" : "muted"}`}>
                      {done} of {rows.length} chakras done
                    </span>
                  ) : (
                    <span className="muted small">
                      <span className="max-md:hidden">chakra </span>
                      {selected + 1} of {rows.length}
                    </span>
                  )}
                </div>
              </div>
              <div className="hl-cc-panel-b">
                {isWrapUp ? (
                  <WrapUp closure={closure} />
                ) : (
                  <PickerHost.Provider value={panel}>
                    <ChakraDetail
                      key={row.chakra}
                      row={row}
                      colors={colors}
                      picksFor={picksFor}
                      onChange={(field, value) => updateRow(selected, field, value)}
                    />
                  </PickerHost.Provider>
                )}
                {isWrapUp && !allDone && (
                  <p role="note" className="muted small mt-3">Add why you worked on each chakra to close the case.</p>
                )}
              </div>
              <div className="hl-cc-foot">
                {selected > 0 ? (
                  <Button type="button" variant="outline" className="hl-btn44" onClick={() => open(selected - 1)} disabled={busy}>
                    {nameOf(selected - 1)}
                  </Button>
                ) : (
                  <span />
                )}
                {isWrapUp ? (
                  closeButton("hl-btn44 hl-grow")
                ) : (
                  <Button type="button" className="hl-btn44 hl-grow" onClick={saveAndNext} disabled={busy}>
                    {save.isPending ? "Saving..." : `Save and next: ${nameOf(selected + 1)}`}
                  </Button>
                )}
              </div>
            </>
          )}
        </section>
      </div>

      {!detailOpen && <div className="hl-cc-pin md:hidden">{closeButton("hl-btn48 w-full")}</div>}
    </div>
  );
}
