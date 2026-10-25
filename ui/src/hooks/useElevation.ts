import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import api from "../lib/axios";
import { useCan, useMyPermissions } from "./useMyPermissions";

export type ElevatableResource = "MASTER_DATA_CHAKRAS" | "MASTER_DATA_COLORS" | "MASTER_DATA_PROTOCOLS";

const PATHS: Record<ElevatableResource, string> = {
  MASTER_DATA_CHAKRAS: "chakras",
  MASTER_DATA_COLORS: "colors",
  MASTER_DATA_PROTOCOLS: "protocols",
};

/**
 * "Unlock shared entries" for one master-data type: after confirming their password, a user
 * whose role holds ELEVATE may edit that type's system rows until the unlock runs out.
 * The server checks every save; this only decides what to show.
 */
export function useElevation(resource: ElevatableResource) {
  const { data } = useMyPermissions();
  const can = useCan();
  const queryClient = useQueryClient();
  const [now, setNow] = useState(() => Date.now());

  const until = data?.elevations?.[resource];
  const untilMs = until ? Date.parse(until) : 0;
  const unlocked = untilMs > now;

  // Keep the minutes-left current, and lock again the moment the unlock ends.
  useEffect(() => {
    if (!unlocked) return;
    const tick = setInterval(() => setNow(Date.now()), 30_000);
    const end = setTimeout(() => setNow(Date.now()), Math.max(0, untilMs - Date.now()) + 50);
    return () => {
      clearInterval(tick);
      clearTimeout(end);
    };
  }, [unlocked, untilMs]);

  const unlock = useMutation({
    mutationFn: (password: string) =>
      api.post(`/api/v1/masterdata/${PATHS[resource]}/elevation`, { password }),
    onSuccess: () => {
      setNow(Date.now());
      return queryClient.invalidateQueries({ queryKey: ["my-permissions"] });
    },
  });

  return {
    canUnlock: can(resource, "ELEVATE"),
    unlocked,
    minutesLeft: unlocked ? Math.max(1, Math.ceil((untilMs - now) / 60_000)) : 0,
    unlock,
  };
}

export type Elevation = ReturnType<typeof useElevation>;
