import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

// -- Interfaces -------------------------------------------------------------------------

// The key is now a runtime value sourced from the consent_type registry
// (Phase 31.1), not a compile-time set — see ConsentTypeResponse below.
export type ConsentType = string;

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

export interface ConsentTypeResponse {
  id: string;
  key: string;
  label: string;
  mandatory: boolean;
  active: boolean;
  systemDefined: boolean;
  createdAt: string;
}

// -- Query Keys -------------------------------------------------------------------------

const POLICY_VERSIONS_QK = ["policy-versions", "active"] as const;
const CONSENT_TYPES_QK = ["consent-types"] as const;
const consentTypesKey = (includeInactive: boolean) =>
  [...CONSENT_TYPES_QK, includeInactive] as const;

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
      toast.success(`Version ${data.policyVersion} published for ${data.consentType}.`);
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to publish. Please try again.", {
        duration: 5000,
      }),
  });
}

// -- Consent Types (registry) -------------------------------------------------------------

export function useConsentTypes(includeInactive = false) {
  return useQuery<ConsentTypeResponse[]>({
    queryKey: consentTypesKey(includeInactive),
    queryFn: () =>
      api
        .get("/api/v1/consents/types", { params: { includeInactive } })
        .then((r) => r.data),
  });
}

export function useCreateConsentType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: { label: string }) =>
      api
        .post("/api/v1/consents/types", payload)
        .then((r) => r.data as ConsentTypeResponse),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: CONSENT_TYPES_QK });
      toast.success(`Consent form "${data.label}" created.`);
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to create consent form.", { duration: 5000 }),
  });
}

export function useRenameConsentType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, label }: { id: string; label: string }) =>
      api
        .patch(`/api/v1/consents/types/${id}/label`, { label })
        .then((r) => r.data as ConsentTypeResponse),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: CONSENT_TYPES_QK });
      toast.success("Consent form renamed.");
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to rename consent form.", { duration: 5000 }),
  });
}

export function useToggleConsentTypeMandatory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, value }: { id: string; value: boolean }) =>
      api
        .patch(`/api/v1/consents/types/${id}/mandatory`, null, { params: { value } })
        .then((r) => r.data as ConsentTypeResponse),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: CONSENT_TYPES_QK });
      qc.invalidateQueries({ queryKey: POLICY_VERSIONS_QK });
      toast.success(
        data.mandatory
          ? `"${data.label}" is now mandatory.`
          : `"${data.label}" is now optional.`
      );
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to update consent form.", { duration: 5000 }),
  });
}

export function useToggleConsentTypeActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, value }: { id: string; value: boolean }) =>
      api
        .patch(`/api/v1/consents/types/${id}/active`, null, { params: { value } })
        .then((r) => r.data as ConsentTypeResponse),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: CONSENT_TYPES_QK });
      qc.invalidateQueries({ queryKey: POLICY_VERSIONS_QK });
      toast.success(
        data.active ? `"${data.label}" activated.` : `"${data.label}" deactivated.`
      );
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to update consent form.", { duration: 5000 }),
  });
}
