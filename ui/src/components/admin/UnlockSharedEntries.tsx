import { useState } from "react";
import { LockOpen } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { extractDetail } from "@/lib/utils";
import type { Elevation } from "@/hooks/useElevation";

/**
 * Header control for a master-data tab: "Unlock shared entries" (asks for the password), or,
 * while unlocked, how long is left. Hidden for the super admin, who can always edit them.
 */
export function UnlockSharedEntries({ elevation, isSuperAdmin }: { elevation: Elevation; isSuperAdmin: boolean }) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  if (isSuperAdmin || !elevation.canUnlock) return null;

  if (elevation.unlocked) {
    return (
      <Badge variant="outline" className="shrink-0 gap-1">
        <LockOpen className="w-3.5 h-3.5" />
        Unlocked · {elevation.minutesLeft} min left
      </Badge>
    );
  }

  const close = () => {
    setOpen(false);
    setPassword("");
    setError(null);
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    elevation.unlock.mutate(password, {
      onSuccess: close,
      onError: (err) => setError(extractDetail(err) || "Could not unlock. Please try again."),
    });
  };

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)} className="px-3 py-2 h-auto shrink-0">
        <LockOpen className="w-4 h-4" />
        <span className="hidden sm:inline">Unlock shared entries</span>
      </Button>
      <Dialog open={open} onOpenChange={(o) => !o && close()}>
        <DialogContent className="max-w-sm">
          <DialogTitle>Unlock shared entries</DialogTitle>
          <form onSubmit={submit} className="space-y-4">
            <p className="text-sm text-text-light">
              Enter your password to edit the shared (system) entries for a limited time.
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="unlock-password">Password</Label>
              <Input
                id="unlock-password"
                type="password"
                autoComplete="current-password"
                autoFocus
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                aria-invalid={!!error || undefined}
              />
              {error && <p className="text-sm text-red-500">{error}</p>}
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" disabled={!password || elevation.unlock.isPending}>
                Unlock
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
