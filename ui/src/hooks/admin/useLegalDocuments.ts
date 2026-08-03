import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

// -- Interfaces -------------------------------------------------------------------------

export type ConsentType =
  | "TERMS_AND_CONDITIONS"
  | "PRIVACY_POLICY"
  | "MEDICAL_DISCLAIMER"
  | "PRACTITIONER_AGREEMENT";

export interface PolicyVersionResponse {
  id: string;
  consentType: ConsentType;
  policyVersion: number;
  mandatory: boolean;
  effectiveDate: string;
  content: string | null;
  publisherId: string | null;
}

export interface PublishPolicyPayload {
  consentType: ConsentType;
  file?: File;
  content?: string;
  mandatory?: boolean;
}

export const CONSENT_TYPE_LABELS: Record<ConsentType, string> = {
  TERMS_AND_CONDITIONS: "Terms & Conditions",
  PRIVACY_POLICY: "Privacy Policy",
  MEDICAL_DISCLAIMER: "Medical Disclaimer",
  PRACTITIONER_AGREEMENT: "Practitioner Agreement",
};

// -- Query Keys -------------------------------------------------------------------------

const POLICY_VERSIONS_QK = ["policy-versions", "active"] as const;

// -- Active Policy Versions ---------------------------------------------------------------

export function useActivePolicyVersions() {
  return useQuery<PolicyVersionResponse[]>({
    queryKey: POLICY_VERSIONS_QK,
    queryFn: () =>
      api.get("/api/v1/consents/policy-versions/active").then((r) => r.data),
  });
}

// -- Publish Policy Version ---------------------------------------------------------------

export function usePublishPolicyVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PublishPolicyPayload) => {
      const formData = new FormData();
      formData.append("consentType", payload.consentType);
      if (payload.file) formData.append("file", payload.file);
      if (payload.content !== undefined) formData.append("content", payload.content);
      if (payload.mandatory !== undefined) {
        formData.append("mandatory", String(payload.mandatory));
      }
      return api
        .post("/api/v1/consents/policy-versions", formData, {
          headers: { "Content-Type": undefined },
        })
        .then((r) => r.data as PolicyVersionResponse);
    },
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: POLICY_VERSIONS_QK });
      toast.success(
        `Version ${data.policyVersion} published for ${CONSENT_TYPE_LABELS[data.consentType]}.`
      );
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to publish. Please try again.", {
        duration: 5000,
      }),
  });
}
