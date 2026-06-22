import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api from "../../lib/axios";

// ── Interfaces ────────────────────────────────────────────────────────────────

export interface PranaColor {
  id: string;
  code: string;
  name: string;
  colorHex: string;
  properties?: string;
  clinicId?: string | null;
}

export interface Chakra {
  id: string;
  code: string;
  name: string;
  category?: string;
  location?: string;
  clinicId?: string | null;
}

export interface ProtocolMapping {
  id: string;
  protocolName: string;
  colorId: string;
  usageType: "CLEANSING" | "ENERGISING";
  clinicId?: string | null;
}

export interface PranaColorPayload {
  name: string;
  code: string;
  colorHex: string;
  properties?: string;
  clinicId?: string | null;
}

export interface ChakraPayload {
  name: string;
  code: string;
  category?: string;
  location?: string;
  clinicId?: string | null;
}

export interface ProtocolMappingPayload {
  protocolName: string;
  colorIds: string[];
  usageType: "CLEANSING" | "ENERGISING";
  clinicId?: string | null;
}

// ── Query Keys ────────────────────────────────────────────────────────────────

const COLOR_QK = ["master-colors"] as const;
const CHAKRA_QK = ["master-chakras"] as const;
const MAPPING_QK = ["master-mappings"] as const;

// ── Prana Colors ─────────────────────────────────────────────────────────────

export function usePranaColors() {
  return useQuery<PranaColor[]>({
    queryKey: COLOR_QK,
    queryFn: () =>
      api.get("/api/v1/masterdata/colors").then((r) => r.data.data ?? r.data),
  });
}

export function useCreateColor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PranaColorPayload) =>
      api.post("/api/v1/masterdata/colors", payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: COLOR_QK }),
  });
}

export function useUpdateColor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: PranaColorPayload }) =>
      api.put(`/api/v1/masterdata/colors/${id}`, payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: COLOR_QK }),
  });
}

export function useDeleteColor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/colors/${id}`).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: COLOR_QK }),
  });
}

// ── Chakras ──────────────────────────────────────────────────────────────────

export function useChakras() {
  return useQuery<Chakra[]>({
    queryKey: CHAKRA_QK,
    queryFn: () =>
      api.get("/api/v1/masterdata/chakras").then((r) => r.data.data ?? r.data),
  });
}

export function useCreateChakra() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ChakraPayload) =>
      api.post("/api/v1/masterdata/chakras", payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: CHAKRA_QK }),
  });
}

export function useUpdateChakra() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ChakraPayload }) =>
      api.put(`/api/v1/masterdata/chakras/${id}`, payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: CHAKRA_QK }),
  });
}

export function useDeleteChakra() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/chakras/${id}`).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: CHAKRA_QK }),
  });
}

// ── Protocol Color Mappings ───────────────────────────────────────────────────

export function useProtocolMappings() {
  return useQuery<ProtocolMapping[]>({
    queryKey: MAPPING_QK,
    queryFn: () =>
      api.get("/api/v1/masterdata/protocol-mappings").then((r) => r.data.data ?? r.data),
  });
}

export function useCreateMapping() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProtocolMappingPayload) =>
      api.post("/api/v1/masterdata/protocol-mappings", payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: MAPPING_QK }),
  });
}

export function useDeleteMapping() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/protocol-mappings/${id}`).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: MAPPING_QK }),
  });
}
