import { useState, useEffect } from "react";
import { ChakraOption, ColorOption } from "../interfaces";
import api from "../lib/axios";

let colorsCache: ColorOption[] | null = null;
let chakrasCache: ChakraOption[] | null = null;
let fetchPromise: Promise<[ColorOption[], ChakraOption[]]> | null = null;

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
        });
      return;
    }

    setLoading(true);
    fetchPromise = Promise.all([
      api.get("/api/v1/masterdata/colors").then((res) => res.data),
      api.get("/api/v1/masterdata/chakras").then((res) => res.data?.data),
    ]);

    fetchPromise
      .then(([colorsData, chakrasData]) => {
        colorsCache = colorsData;
        chakrasCache = chakrasData;
        console.log(colorsData);
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
