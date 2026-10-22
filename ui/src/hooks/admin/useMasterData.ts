import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";
import type { SpringPageResponse } from "../../types/pagination";

// -- Interfaces -------------------------------------------------------------------------

export interface PranaColor {
  id: string;
  code: string;
  name: string;
  colorHex: string;
  properties?: string[];
  healerId?: string | null;
}

export interface Chakra {
  id: string;
  code: string;
  name: string;
  category?: string;
  location?: string;
  healerId?: string | null;
}

export interface ProtocolMapping {
  id: string;
  protocolName: string;
  colorId: string;
  usageType: "CLEANSING" | "ENERGISING";
  healerId?: string | null;
}

export interface PranaColorPayload {
  name: string;
  code: string;
  colorHex: string;
  properties?: string[];
  healerId?: string | null;
}

export interface ChakraPayload {
  name: string;
  code: string;
  category?: string;
  location?: string;
  healerId?: string | null;
}

export interface ProtocolMappingPayload {
  protocolName: string;
  colorIds: string[];
  usageType: "CLEANSING" | "ENERGISING";
  healerId?: string | null;
  chakraName: string;
}

export interface Protocol {
  id: string;
  name: string;
  sourceBook: string;
  pageNumber: number;
  category?: string;
  description?: string;
  chapter?: string;
  protocolType: "PROTOCOL" | "SUPPLEMENTARY";
  healerId?: string | null;
}

export interface ProtocolPayload {
  name: string;
  sourceBook: string;
  pageNumber: number;
  category?: string;
  description?: string;
  chapter?: string;
  protocolType: "PROTOCOL" | "SUPPLEMENTARY";
  healerId?: string | null;
}

export interface ProtocolImportResult {
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errors: string[];
}

/** One submitted bulk protocol import, tracked async — the "Imports" tab's list/status view. */
export interface ProtocolImportJobView {
  id: string;
  originalFilename: string | null;
  status: "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
  totalRows: number | null;
  created: number | null;
  updated: number | null;
  skipped: number | null;
  errors: string[];
  failureMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

/** One chakra linked to a protocol, with its cleansing/energising colors — the single-protocol
 * "manage chakras/colors" admin view. `healerId` null means a system (bulk-imported) row,
 * read-only for anyone without the SUPADM override; non-null means it belongs to that healer
 * and is editable/deletable by them alone (or SUPADM). */
export interface ProtocolChakraColorGroup {
  protocolChakraId: string;
  chakraId: string;
  chakraName: string;
  healerId: string | null;
  cleansingColorIds: string[];
  energisingColorIds: string[];
}

export interface ProtocolChakraColorEntryPayload {
  chakraId: string;
  cleansingColorIds: string[];
  energisingColorIds: string[];
}

// -- Query Keys -------------------------------------------------------------------------

export const COLOR_QK = ["master-colors"] as const;
export const CHAKRA_QK = ["master-chakras"] as const;
const MAPPING_QK = ["master-mappings"] as const;
const PROTOCOL_QK = ["master-protocols"] as const;

// -- Prana Colors -----------------------------------------------------------------------

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
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: COLOR_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useUpdateColor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: PranaColorPayload }) =>
      api.put(`/api/v1/masterdata/colors/${id}`, payload).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: COLOR_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useDeleteColor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/colors/${id}`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: COLOR_QK });
      toast.success("Deleted");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to delete", { duration: 5000 }),
  });
}

// -- Chakras ----------------------------------------------------------------------------

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
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: CHAKRA_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useUpdateChakra() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ChakraPayload }) =>
      api.put(`/api/v1/masterdata/chakras/${id}`, payload).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: CHAKRA_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useDeleteChakra() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/chakras/${id}`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: CHAKRA_QK });
      toast.success("Deleted");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to delete", { duration: 5000 }),
  });
}

// -- Protocol Color Mappings ------------------------------------------------------------

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
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: MAPPING_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useDeleteMapping() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/protocol-mappings/${id}`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: MAPPING_QK });
      toast.success("Deleted");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to delete", { duration: 5000 }),
  });
}

// -- Protocols ----------------------------------------------------------------------------

export function useProtocols(type?: "PROTOCOL" | "SUPPLEMENTARY") {
  return useQuery<Protocol[]>({
    queryKey: [...PROTOCOL_QK, type ?? "all"],
    queryFn: () =>
      api
        .get("/api/v1/masterdata/protocols", { params: type ? { type } : {} })
        .then((r) => r.data.data ?? r.data),
  });
}

/** One page of the protocol catalog, searched on the server by name, book or chapter. */
export function useProtocolsPage(query: string, page: number, size = 50) {
  return useQuery<SpringPageResponse<Protocol>>({
    queryKey: [...PROTOCOL_QK, "page", query, page, size],
    queryFn: () =>
      api
        .get("/api/v1/masterdata/protocols/page", { params: { q: query || undefined, page, size } })
        .then((r) => r.data.data),
    placeholderData: keepPreviousData,
  });
}

export function useCreateProtocol() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProtocolPayload) =>
      api.post("/api/v1/masterdata/protocols", payload).then((r) => r.data.data as Protocol),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: PROTOCOL_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useUpdateProtocol() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ProtocolPayload }) =>
      api.put(`/api/v1/masterdata/protocols/${id}`, payload).then((r) => r.data.data as Protocol),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: PROTOCOL_QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useDeleteProtocol() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/masterdata/protocols/${id}`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: PROTOCOL_QK });
      toast.success("Deleted");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to delete", { duration: 5000 }),
  });
}

// -- Protocol import jobs (async) --------------------------------------------------------
// Submitting no longer blocks on the parse+upsert: the request returns a QUEUED job
// immediately and the "Imports" tab polls its status. A healer can submit several imports;
// each gets its own independent job.

const IMPORT_JOBS_QK = ["master-protocol-import-jobs"] as const;

export function useSubmitProtocolImport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return api
        .post("/api/v1/masterdata/protocols/import", formData, {
          headers: { "Content-Type": undefined },
        })
        .then((r) => r.data.data as ProtocolImportJobView);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: IMPORT_JOBS_QK });
      toast.success("Import queued");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to queue import", { duration: 5000 }),
  });
}

export function useProtocolImportJobs() {
  const qc = useQueryClient();
  return useQuery<ProtocolImportJobView[]>({
    queryKey: IMPORT_JOBS_QK,
    queryFn: () =>
      api.get("/api/v1/masterdata/protocols/import-jobs").then((r) => {
        const jobs = (r.data.data ?? r.data) as ProtocolImportJobView[];
        // A completed/failed import may have created chakra/color/protocol rows (the file can
        // carry Chakras Used / Cleansing Color / Energizing Color columns) — refresh those
        // tabs too whenever any job is no longer in flight, not just on the submit response
        // (which fires before any of that work has actually happened).
        if (jobs.some((j) => j.status === "COMPLETED" || j.status === "FAILED")) {
          qc.invalidateQueries({ queryKey: PROTOCOL_QK });
          qc.invalidateQueries({ queryKey: CHAKRA_QK });
          qc.invalidateQueries({ queryKey: COLOR_QK });
        }
        return jobs;
      }),
    // Poll only while something is actually in flight — stop once every job is terminal.
    refetchInterval: (query) => {
      const jobs = query.state.data ?? [];
      const inFlight = jobs.some((j) => j.status === "QUEUED" || j.status === "PROCESSING");
      return inFlight ? 2000 : false;
    },
  });
}

// -- Protocol -> Chakra relationship (read flow) -----------------------------------------

/**
 * Distinct chakras associated with any of the given protocols — used to filter the chakra
 * scan's chakra list down to what the selected protocol(s) actually address, instead of the
 * full catalog. Disabled (no request) when no protocol ids are given.
 */
export function useChakrasForProtocols(protocolIds: string[]) {
  return useQuery<Chakra[]>({
    queryKey: [...CHAKRA_QK, "for-protocols", ...[...protocolIds].sort()],
    enabled: protocolIds.length > 0,
    queryFn: () =>
      api
        // Comma-joined as a single query value (not axios's array serialization) — Spring's
        // @RequestParam List<UUID> binds a single comma-separated value out of the box, and
        // this avoids depending on axios's array-param format matching that on the wire.
        .get("/api/v1/masterdata/protocols/chakras", {
          params: { protocolIds: protocolIds.join(",") },
        })
        .then((r) => r.data.data ?? r.data),
  });
}

// -- Protocol <-> Chakra <-> Color (single-protocol manage view) ------------------------
// Distinct from the read flow above: the "select one protocol, add/edit/remove its chakras
// and colors" admin surface (MasterDataPage's Protocols tab). A healer's own additions here
// are visible and editable only by them (or SUPADM); system (bulk-imported) links stay
// read-only for everyone else.

function chakraColorGroupsQueryKey(protocolId: string) {
  return [...PROTOCOL_QK, "chakra-colors", protocolId] as const;
}

export function useChakraColorGroupsForProtocol(protocolId: string | undefined) {
  return useQuery<ProtocolChakraColorGroup[]>({
    queryKey: chakraColorGroupsQueryKey(protocolId ?? ""),
    enabled: !!protocolId,
    queryFn: () =>
      api
        .get(`/api/v1/masterdata/protocols/${protocolId}/chakra-colors`)
        .then((r) => r.data.data ?? r.data),
  });
}

export function useAddProtocolChakraColors(protocolId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entries: ProtocolChakraColorEntryPayload[]) =>
      api
        .post(`/api/v1/masterdata/protocols/${protocolId}/chakra-colors`, { entries })
        .then((r) => r.data.data as ProtocolChakraColorGroup[]),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: chakraColorGroupsQueryKey(protocolId ?? "") });
      toast.success("Added");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to add", { duration: 5000 }),
  });
}

export function useUpdateProtocolChakraColor(protocolId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      protocolChakraId,
      cleansingColorIds,
      energisingColorIds,
    }: {
      protocolChakraId: string;
      cleansingColorIds: string[];
      energisingColorIds: string[];
    }) =>
      api
        .put(`/api/v1/masterdata/protocols/${protocolId}/chakra-colors/${protocolChakraId}`, {
          cleansingColorIds,
          energisingColorIds,
        })
        .then((r) => r.data.data as ProtocolChakraColorGroup[]),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: chakraColorGroupsQueryKey(protocolId ?? "") });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useDeleteProtocolChakraColor(protocolId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (protocolChakraId: string) =>
      api
        .delete(`/api/v1/masterdata/protocols/${protocolId}/chakra-colors/${protocolChakraId}`)
        .then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: chakraColorGroupsQueryKey(protocolId ?? "") });
      toast.success("Removed");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to remove", { duration: 5000 }),
  });
}

// -- Bulk wipe (SUPADM only) --------------------------------------------------------------

export type MasterDataType = "ALL" | "CHAKRAS" | "COLORS" | "MAPPINGS" | "PROTOCOLS";

export interface MasterDataWipeResult {
  type: MasterDataType;
  chakrasDeleted: number;
  colorsDeleted: number;
  mappingsDeleted: number;
  protocolsDeleted: number;
}

/** SUPADM-only — the backend rejects any other caller regardless of what this hook sends. */
export function useWipeMasterData() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (type: MasterDataType) =>
      api
        .delete("/api/v1/masterdata/wipe", { params: { type } })
        .then((r) => r.data.data as MasterDataWipeResult),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: PROTOCOL_QK });
      qc.invalidateQueries({ queryKey: CHAKRA_QK });
      qc.invalidateQueries({ queryKey: COLOR_QK });
      qc.invalidateQueries({ queryKey: MAPPING_QK });
      toast.success("Master data wiped");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to wipe master data", { duration: 5000 }),
  });
}
