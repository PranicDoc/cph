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

/** The healer's most-used (colour, property) pairs from their other cases. Empty while loading or on error. */
export function useUsualPicks(healingCaseId: string) {
  const { data } = useQuery<UsualPicks>({
    queryKey: ["usual-picks", healingCaseId],
    queryFn: async () => (await api.get(`/api/v1/cases/${healingCaseId}/protocol-understanding/usual-picks`)).data,
    enabled: !!healingCaseId,
    retry: false,
  });
  return (chakra: string, category: UsualPickCategory): UsualPick[] => {
    const own = data?.byChakra?.[chakra]?.[category];
    return (own?.length ? own : (data?.overall?.[category] ?? [])).slice(0, 4);
  };
}
