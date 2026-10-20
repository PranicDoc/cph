import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import type { ValidityUnit } from "../lib/validity";

export interface PackageResponse {
  id: string;
  name: string;
  credits: number;
  price_paise: number;
  currency: string;
  active: boolean;
  display_order: number;
  validity_value: number | null;
  validity_unit: ValidityUnit | null;
}

export function useCreditPackages() {
  return useQuery<PackageResponse[]>({
    queryKey: ["credit-packages"],
    queryFn: () => api.get("/api/credits/packages").then((r) => JSON.parse(r?.data?.data)),
    staleTime: 5 * 60 * 1000,
  });
}
