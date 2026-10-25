import { useQuery } from "@tanstack/react-query";
import type { ChakraOption, ColorOption } from "../interfaces";
import api from "../lib/axios";
import { CHAKRA_QK, COLOR_QK } from "./admin/useMasterData";

type ApiListResponse<T> = T[] | { data?: T[] };

// Stable empty arrays so consumers' effects/memos don't re-run on every render while loading.
const EMPTY_COLORS: ColorOption[] = [];
const EMPTY_CHAKRAS: ChakraOption[] = [];

function unwrapList<T>(response: ApiListResponse<T>): T[] {
  if (Array.isArray(response)) {
    return response;
  }

  return Array.isArray(response?.data) ? response.data : [];
}

function normalizeChakra(item: any): ChakraOption {
  return {
    id: item.id ?? "",
    code: item.code ?? "",
    name: item.name ?? "",
    category: item.category ?? undefined,
    location: item.location ?? undefined,
    healerId: item.healerId ?? null,
  };
}

function normalizeColor(item: any): ColorOption {
  return {
    id: item.id ?? "",
    code: item.code ?? "",
    name: item.name ?? "",
    colorHex: item.colorHex ?? undefined,
    properties: item.properties ?? undefined,
    category: item.category ?? null,
    healerId: item.healerId ?? null,
  };
}

/**
 * Colors + chakras for the session wizard / closure form. Backed by TanStack Query under the
 * same "master-colors" / "master-chakras" key prefixes the admin master-data hooks invalidate,
 * so a wipe, import or edit refreshes this data too (the old module-level cache never did).
 */
export function useReferenceData() {
  const colorsQuery = useQuery<ColorOption[]>({
    queryKey: [...COLOR_QK, "reference"],
    queryFn: () =>
      api
        .get<ApiListResponse<any>>("/api/v1/masterdata/colors")
        .then((res) => unwrapList(res.data).map(normalizeColor).filter((color) => color.id)),
  });
  const chakrasQuery = useQuery<ChakraOption[]>({
    queryKey: [...CHAKRA_QK, "reference"],
    // Backend is the sole source of truth for which chakras exist — no client-side
    // allowlist. Only a structurally invalid row (no id/name) is dropped.
    queryFn: () =>
      api
        .get<ApiListResponse<any>>("/api/v1/masterdata/chakras")
        .then((res) => unwrapList(res.data).map(normalizeChakra).filter((chakra) => chakra.id && chakra.name)),
  });

  return {
    colors: colorsQuery.data ?? EMPTY_COLORS,
    chakras: chakrasQuery.data ?? EMPTY_CHAKRAS,
    loading: colorsQuery.isPending || chakrasQuery.isPending,
    error: (colorsQuery.error ?? chakrasQuery.error) as Error | null,
  };
}
