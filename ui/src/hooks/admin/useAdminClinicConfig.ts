import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api from "../../lib/axios";

export interface FallbackPolicy {
  clinic_id: string;
  allow_individual_fallback: boolean;
  updated_at: string;
}

export function useFallbackPolicy(clinicId: string | undefined) {
  return useQuery<FallbackPolicy>({
    queryKey: ["fallback-policy", clinicId],
    queryFn: () =>
      api
        .get(`/api/admin/credit/accounts/clinic/${clinicId}/fallback`)
        .then((r) => r.data),
    enabled: !!clinicId,
  });
}

export function useUpsertFallbackPolicy(clinicId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (allowIndividualFallback: boolean) =>
      api
        .put(`/api/admin/credit/accounts/clinic/${clinicId}/fallback`, {
          allow_individual_fallback: allowIndividualFallback,
        })
        .then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fallback-policy", clinicId] }),
  });
}

export function useAdminAdjust() {
  return useMutation({
    mutationFn: ({
      ownerType,
      ownerId,
      amount,
      notes,
    }: {
      ownerType: string;
      ownerId: string;
      amount: number;
      notes?: string;
    }) =>
      api
        .post(`/api/admin/credit/accounts/${ownerType}/${ownerId}/adjust`, {
          owner_type: ownerType,
          owner_id: ownerId,
          amount,
          notes,
        })
        .then((r) => r.data),
  });
}

export function useAdminPaygToggle() {
  return useMutation({
    mutationFn: ({
      ownerType,
      ownerId,
      paygEnabled,
      bundleCredits,
    }: {
      ownerType: string;
      ownerId: string;
      paygEnabled: boolean;
      bundleCredits?: number;
    }) =>
      api
        .put(`/api/admin/credit/accounts/${ownerType}/${ownerId}/payg`, {
          owner_type: ownerType,
          owner_id: ownerId,
          payg_enabled: paygEnabled,
          bundle_credits: bundleCredits,
        })
        .then((r) => r.data),
  });
}
