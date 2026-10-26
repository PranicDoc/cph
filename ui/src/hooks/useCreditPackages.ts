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

/** The default plan every account starts on. Other free-priced packages (a trial) must be claimed. */
export const isBuiltInFreePlan = (pkg: Pick<PackageResponse, "name">) =>
  pkg.name.toUpperCase() === "FREE";

export function useCreditPackages() {
  return useQuery<PackageResponse[]>({
    queryKey: ["credit-packages"],
    queryFn: () => api.get("/api/credits/packages").then((r) => JSON.parse(r?.data?.data)),
    staleTime: 5 * 60 * 1000,
  });
}
