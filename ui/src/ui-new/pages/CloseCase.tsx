import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Patient } from "@/context/PatientContext";
import { useProtocolColors, useProtocolUnderstanding } from "@/hooks/useProtocolUnderstanding";
import { ClosurePopup } from "@/pages/Closure";
import ProtocolUnderstanding from "../closure/ProtocolUnderstanding";

/**
 * New-view first step of closing a case, as a full page: the healer's protocol understanding for each
 * scanned chakra. Saving it opens the closure dialog at its last step.
 */
export default function CloseCase() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { healingCaseId = "" } = useParams<{ healingCaseId: string }>();
  const patient = (useLocation().state as { patient?: Patient } | null)?.patient;
  const { rows, ready, loading, updateRow, save } = useProtocolUnderstanding(healingCaseId);
  const colorProps = useProtocolColors();
  const [closing, setClosing] = useState(false);
  const reasonMissing = rows.some((row) => !row.reason?.trim());

  return (
    <div className="hlx mx-auto max-w-3xl pb-10">
      <div className="space-y-2">
        <Button type="button" variant="ghost" onClick={() => navigate(-1)} className="h-auto px-0 gap-1.5 text-sm">
          <ArrowLeft className="w-4 h-4" />
          Back
        </Button>
        <h1 className="text-xl md:text-2xl font-bold text-primary-dark">
          Protocol understanding{patient ? ` — ${patient.firstName} ${patient.lastName}` : ""}
        </h1>
        <p className="text-sm text-text-light">
          The colours used on each scanned chakra, and why you worked on it. Save to go on to closing the case.
        </p>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-4 md:p-6 mt-4">
        {ready && <ProtocolUnderstanding rows={rows} {...colorProps} onChange={updateRow} />}
      </div>

      {ready && reasonMissing && (
        <p role="note" className="text-sm text-text-light text-right mt-3">Add why you worked on each chakra to continue.</p>
      )}

      <div className="flex justify-end gap-2 mt-4">
        <Button type="button" variant="outline" onClick={() => navigate(-1)}>
          Cancel
        </Button>
        <Button
          type="button"
          onClick={() => save.mutate(undefined, { onSuccess: () => setClosing(true) })}
          disabled={save.isPending || !ready || reasonMissing}
        >
          {save.isPending ? "Saving..." : loading ? "Loading..." : "Save"}
        </Button>
      </div>

      {closing && (
        <ClosurePopup
          patient={{ ...patient, healingCaseId } as Patient}
          skipUnderstanding
          onClose={() => setClosing(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ["patients"] });
            navigate(-1);
          }}
        />
      )}
    </div>
  );
}
