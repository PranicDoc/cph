import { useState, useEffect } from "react";
import { getCookie, setCookie } from "../lib/utils";

interface UsageMap {
  [chakraName: string]: number;
}

export function useChakraPreferences(
  availableChakras: { name: string; color: string }[]
) {
  const [usageMap, setUsageMap] = useState<UsageMap>({});

  useEffect(() => {
    const stored = getCookie("chakra_usage");
    if (stored) {
      try {
        setUsageMap(JSON.parse(stored));
      } catch (e) {}
    }
  }, []);

  useEffect(() => {
    if (Object.keys(usageMap).length > 0) {
      setCookie("chakra_usage", JSON.stringify(usageMap), 365);
    }
  }, [usageMap]);

  const recordUsage = (chakraName: string) => {
    setUsageMap((prev) => ({
      ...prev,
      [chakraName]: (prev[chakraName] || 0) + 1,
    }));
  };

  const getSortedChakras = (): { name: string; color: string }[] => {
    return [...availableChakras].sort((a, b) => {
      const freqA = usageMap[a.name] || 0;
      const freqB = usageMap[b.name] || 0;
      if (freqA !== freqB) return freqB - freqA;
      return availableChakras.indexOf(a) - availableChakras.indexOf(b);
    });
  };

  return { recordUsage, getSortedChakras };
}
