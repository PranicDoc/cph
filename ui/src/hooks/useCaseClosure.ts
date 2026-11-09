import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { extractDetail } from "@/lib/utils";
import api from "@/lib/axios";
import { useProtocols, type Protocol } from "@/hooks/admin/useMasterData";
import { reconstructSelectedProtocols } from "@/lib/protocolSelection";

// The recommended protocols are saved at the top of the closing comments, in this form.
const PROTOCOL_NOTE = "The patient is advised to follow: \n";

/** Splits saved comments back into the recommended protocol names and the healer's own text. */
export function splitClosureComments(saved: string): { protocolNames: string; comments: string } {
  if (!saved.startsWith(PROTOCOL_NOTE)) return { protocolNames: "", comments: saved };
  const rest = saved.slice(PROTOCOL_NOTE.length);
  const end = rest.indexOf("\n\n");
  return end < 0
    ? { protocolNames: rest, comments: "" }
    : { protocolNames: rest.slice(0, end), comments: rest.slice(end + 2) };
}

/**
 * The closing step's state: recommended protocols, comments and "healing continued", loaded from
 * the case's existing closure when there is one, and the mutation that closes the case with them.
 */
export function useCaseClosure(healingCaseId: string, onClosed: () => void) {
  const [comments, setComments] = useState("");
  const [isHealingContinued, setIsHealingContinued] = useState(false);
  const [selectedProtocols, setSelectedProtocols] = useState<Protocol[]>([]);
  const [savedProtocolNames, setSavedProtocolNames] = useState("");
  // Closing before the existing closure is in (or after it failed to load) would overwrite it.
  const [loaded, setLoaded] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  // The case already has a closure (closed or continuing): saving it again is an edit.
  const [hasClosure, setHasClosure] = useState(false);
  const { data: supplementaryProtocols = [] } = useProtocols("SUPPLEMENTARY");

  // Show the saved recommendations as selected (once the catalog is in), so saving again
  // doesn't write the protocol line twice.
  useEffect(() => {
    if (savedProtocolNames) {
      setSelectedProtocols(reconstructSelectedProtocols(savedProtocolNames, supplementaryProtocols));
    }
  }, [savedProtocolNames, supplementaryProtocols]);

  useEffect(() => {
    /**
     * A case previously closed with "healing continued" checked already has a
     * CaseClosure row -- closing it a second time updates that same row (see
     * CaseService.closeCase), so reopening this popup should show what was
     * submitted last time instead of a blank form.
     */
    let cancelled = false;
    const fetchExistingClosure = async () => {
      try {
        const { data } = await api.get(`/api/v1/cases/${healingCaseId}`);
        if (cancelled) return;
        setLoaded(true);
        if (!data?.closure) return;
        setHasClosure(true);
        const saved = splitClosureComments(data.closure.additionalComments ?? "");
        setComments(saved.comments);
        setSavedProtocolNames(saved.protocolNames);
        setIsHealingContinued(Boolean(data.closure.isHealingContinued));
      } catch (err) {
        if (!cancelled) setLoadFailed(true);
        toast.error(
          extractDetail(err) || "Failed to load existing closure details",
          { duration: 5000 }
        );
      }
    };

    fetchExistingClosure();
    return () => {
      cancelled = true;
    };
  }, [healingCaseId]);

  const closeMutation = useMutation({
    mutationFn: () => {
      const protocolNames = selectedProtocols.map((p) => p.name).join(", ");
      const protocolNote = protocolNames
        ? `${PROTOCOL_NOTE}${protocolNames}`
        : "";
      const finalComments = [protocolNote, comments.trim()]
        .filter(Boolean)
        .join("\n\n");
      return api.post(`/api/v1/cases/${healingCaseId}/close`, {
        additionalComments: finalComments,
        isHealingContinued,
      });
    },
    onSuccess: () => {
      toast.success("Case closed");
      onClosed();
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to close case", {
        duration: 5000,
      }),
  });

  return {
    loaded,
    loadFailed,
    hasClosure,
    comments,
    setComments,
    isHealingContinued,
    setIsHealingContinued,
    selectedProtocols,
    setSelectedProtocols,
    closeMutation,
  };
}
