import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { extractDetail } from "../lib/utils";

interface EraseAccountDialogProps {
  open: boolean;
  onClose: () => void;
  /** Whose account: the e-mail they must type to confirm. */
  name: string;
  email: string;
  /** What will be lost, in plain words. */
  warning: string;
  onErase: (confirmation: string) => Promise<unknown>;
}

/** Type-to-confirm for the permanent account erase: the button stays off until the e-mail matches. */
export function EraseAccountDialog({ open, onClose, name, email, warning, onErase }: EraseAccountDialogProps) {
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const matches = email !== "" && typed.trim().toLowerCase() === email.trim().toLowerCase();

  const close = () => {
    setTyped("");
    setError("");
    onClose();
  };

  const erase = async () => {
    setBusy(true);
    setError("");
    try {
      await onErase(typed.trim());
      setTyped("");
    } catch (e) {
      setError(extractDetail(e) || "Could not erase the account. Nothing was changed.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !busy && close()}>
      <DialogContent>
        <DialogTitle>Erase {name}&apos;s account permanently?</DialogTitle>
        <DialogDescription>{warning}</DialogDescription>
        <p className="text-sm text-text mt-2">
          This cannot be undone. To confirm, type <strong>{email}</strong> below.
        </p>
        <Input
          aria-label="Confirm e-mail"
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
          placeholder={email}
          autoComplete="off"
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-3 mt-4">
          <Button variant="outline" onClick={close} disabled={busy}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={erase} disabled={!matches || busy}>
            {busy ? "Erasing…" : "Erase permanently"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
