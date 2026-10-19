import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";

export interface PackageResponse {
  id: string;
  name: string;
  credits: number;
  price_paise: number;
  currency: string;
  active: boolean;
  display_order: number;
  validity_days: number | null;
}

export function useCreditPackages() {
  return useQuery<PackageResponse[]>({
    queryKey: ["credit-packages"],
    queryFn: () => api.get("/api/credits/packages").then((r) => JSON.parse(r?.data?.data)),
    staleTime: 5 * 60 * 1000,
  });
}
