import { useEffect, useState } from "react";
import type { ChakraOption, ColorOption } from "../interfaces";
import api from "../lib/axios";

type ApiListResponse<T> = T[] | { data?: T[] };

let colorsCache: ColorOption[] | null = null;
let chakrasCache: ChakraOption[] | null = null;
let fetchPromise: Promise<[ColorOption[], ChakraOption[]]> | null = null;

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
    clinicId: item.clinicId ?? null,
  };
}

function normalizeColor(item: any): ColorOption {
  return {
    id: item.id ?? "",
    code: item.code ?? "",
    name: item.name ?? "",
    colorHex: item.colorHex ?? undefined,
    properties: item.properties ?? undefined,
    clinicId: item.clinicId ?? null,
  };
}

export function useReferenceData() {
  const [colors, setColors] = useState<ColorOption[]>(colorsCache ?? []);
  const [chakras, setChakras] = useState<ChakraOption[]>(chakrasCache ?? []);
  const [loading, setLoading] = useState(!colorsCache || !chakrasCache);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (colorsCache && chakrasCache) {
      setColors(colorsCache);
      setChakras(chakrasCache);
      setLoading(false);
      return;
    }

    if (fetchPromise) {
      fetchPromise
        .then(([colorsData, chakrasData]) => {
          setColors(colorsData);
          setChakras(chakrasData);
          setLoading(false);
        })
        .catch((err) => {
          setError(err);
          setLoading(false);
          fetchPromise = null;
        });
      return;
    }

    setLoading(true);
    fetchPromise = Promise.all([
      api
        .get<ApiListResponse<any>>("/api/v1/masterdata/colors")
        .then((res) =>
          unwrapList(res.data)
            .map(normalizeColor)
            .filter((color) => color.id)
        ),
      api
        .get<ApiListResponse<any>>("/api/v1/masterdata/chakras")
        .then((res) =>
          unwrapList(res.data)
            .map(normalizeChakra)
            .filter((chakra) => chakra.id)
        ),
    ]);

    fetchPromise
      .then(([colorsData, chakrasData]) => {
        colorsCache = colorsData;
        chakrasCache = chakrasData;
        setColors(colorsData);
        setChakras(chakrasData);
        setLoading(false);
      })
      .catch((err) => {
        console.error("Failed to fetch reference data:", err);
        setError(err);
        setLoading(false);
        fetchPromise = null;
      });
  }, []);

  return { colors, chakras, loading, error };
}
