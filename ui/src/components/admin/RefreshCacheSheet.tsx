import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { useCacheNames, useRefreshCache } from "../../hooks/admin/useCacheRefresh";
import { SingleSelect } from "@/components/ui/single-select";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { extractDetail } from "../../lib/utils";

const ALL = "ALL";

/** Super admin: drop cached values after data was changed outside the app, e.g. in the database. */
export function RefreshCacheSheet({ onClose }: { onClose: () => void }) {
  const { data: names } = useCacheNames();
  const refresh = useRefreshCache();
  const [cacheName, setCacheName] = useState(ALL);
  const [key, setKey] = useState("");
  const trimmedKey = key.trim();
  const everything = cacheName === ALL && !trimmedKey;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    try {
      const result = await refresh.mutateAsync({
        ...(cacheName !== ALL && { cacheName }),
        ...(trimmedKey && { key: trimmedKey }),
      });
      if (!result.redisCleared) {
        toast.warning("Cleared in memory, but Redis could not be reached. Try again.");
        return;
      }
      toast.success("Cache refreshed");
      onClose();
    } catch (e) {
      toast.error(extractDetail(e) || "Could not refresh the cache");
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && !refresh.isPending && onClose()}>
      <DialogContent>
        <DialogTitle>Refresh cache</DialogTitle>
        <DialogDescription>
          Use this after changing data directly in the database. The next request loads it again.
        </DialogDescription>
        <form onSubmit={submit} className="space-y-3">
          <SingleSelect
            value={cacheName}
            onChange={setCacheName}
            options={[
              { value: ALL, label: "All caches" },
              ...(names ?? []).map((n) => ({ value: n, label: n })),
            ]}
            aria-label="Cache"
          />
          <div>
            <label htmlFor="cache-key" className="block text-sm font-medium text-text mb-1">
              Key (optional)
            </label>
            <Input
              id="cache-key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              aria-describedby="cache-key-hint"
            />
            <p id="cache-key-hint" className="text-xs text-text-light mt-1">
              For a healer&apos;s name or email, use the healer ID.
            </p>
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <Button type="button" variant="outline" onClick={onClose} disabled={refresh.isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={refresh.isPending}>
              {everything ? "Refresh all caches" : "Refresh"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
