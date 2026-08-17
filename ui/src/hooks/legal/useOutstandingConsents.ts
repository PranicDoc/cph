import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";
import type { PolicyVersionResponse } from "../admin/useLegalDocuments";

/**
 * Frontend half of Phase 34's REG-02/REG-03 legal gate.
 *
 * The outstanding set returned by `GET /api/v1/consents/outstanding` is
 * computed entirely server-side by `ConsentService.outstandingConsents()`,
 * which already reads the dynamic `consent_type.mandatory` flag per
 * CONSENT-10. This file adds no client-side notion of which policies are
 * mandatory — it only reads the server's answer and lets the practitioner
 * record an acceptance for one document at a time.
 */

// -- Query Key ----------------------------------------------------------------------------

export const OUTSTANDING_CONSENTS_QK = ["consents", "outstanding"] as const;

// -- Outstanding Consents -------------------------------------------------------------------

export function useOutstandingConsents(enabled: boolean = true) {
  return useQuery<PolicyVersionResponse[]>({
    queryKey: OUTSTANDING_CONSENTS_QK,
    queryFn: () => api.get("/api/v1/consents/outstanding").then((r) => r.data),
    enabled,
    // T-34-03: the gate must re-verify against the server on every mount
    // rather than trusting the app-wide 5-minute staleTime, since the
    // previously-signed-in account's answer must never be reused.
    staleTime: 0,
    refetchOnMount: "always",
    // T-34-03: dropping the cache entry the instant no component observes
    // it means a second account signing in on the same tab after a logout
    // can never be handed the previous account's outstanding list.
    gcTime: 0,
    // retry left at the app default (false) so a failure settles promptly
    // into an error state instead of hanging the gate.
  });
}

// -- Accept Consent ---------------------------------------------------------------------------

export function useAcceptConsent() {
  const qc = useQueryClient();
  return useMutation({
    // T-34-04: the payload is structurally limited to these two fields —
    // acceptance metadata (subjectId, acceptedAt, ipAddress, userAgent,
    // language) is derived by the server and cannot be supplied here.
    mutationFn: (payload: { consentType: string; policyVersion: number }) =>
      api.post("/api/v1/consents", payload).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: OUTSTANDING_CONSENTS_QK });
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "We couldn't record your acceptance. Please try again.", {
        duration: 5000,
      }),
  });
}
