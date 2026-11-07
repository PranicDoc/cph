import { useQuery } from "@tanstack/react-query";
import api from "@/lib/axios";

export interface UsualPick {
  color: string;
  property: string | null;
}
export type UsualPickCategory = "cleansing" | "energising";
type PicksByCategory = Record<UsualPickCategory, UsualPick[]>;
export interface UsualPicks {
  overall: PicksByCategory;
  byChakra: Record<string, PicksByCategory>;
}

/**
 * The healer's most-used (colour, property) pairs from their other cases: this chakra's first, topped
 * up from all chakras, only colours still in the category's catalog, at most 4. Empty while loading
 * or on error.
 */
export function useUsualPicks(healingCaseId: string) {
  const { data } = useQuery<UsualPicks>({
    queryKey: ["usual-picks", healingCaseId],
    queryFn: async () => (await api.get(`/api/v1/cases/${healingCaseId}/protocol-understanding/usual-picks`)).data,
    enabled: !!healingCaseId,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
  return (chakra: string, category: UsualPickCategory, catalog: string[]): UsualPick[] => {
    const all = [...(data?.byChakra?.[chakra]?.[category] ?? []), ...(data?.overall?.[category] ?? [])];
    const seen = new Set<string>();
    return all
      .filter((p) => {
        const key = `${p.color}|${p.property ?? ""}`;
        if (!catalog.includes(p.color) || seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, 4);
  };
}
