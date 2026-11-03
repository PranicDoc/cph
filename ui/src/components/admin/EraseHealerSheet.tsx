import { useMemo, useState } from "react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { useAdminHealers } from "../../hooks/admin/useAdminHealers";
import { useEraseAccount } from "../../hooks/useEraseAccount";
import { SingleSelect } from "@/components/ui/single-select";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { EraseAccountDialog } from "../EraseAccountDialog";

/** Super admin: pick a healer, then type their e-mail to erase the account and all its data for good. */
export function EraseHealerSheet({ onClose }: { onClose: () => void }) {
  const { data: healers, isLoading } = useAdminHealers(true);
  const erase = useEraseAccount();
  const queryClient = useQueryClient();
  const [healerId, setHealerId] = useState("");
  const [confirming, setConfirming] = useState(false);

  const options = useMemo(
    () =>
      (healers ?? [])
        .slice()
        .sort((a, b) => a.fullName.localeCompare(b.fullName))
        .map((h) => ({ value: h.id, label: `${h.fullName} · ${h.email}` })),
    [healers]
  );
  const healer = healers?.find((h) => h.id === healerId);

  if (confirming && healer) {
    return (
      <EraseAccountDialog
        open
        onClose={() => setConfirming(false)}
        name={healer.fullName}
        email={healer.email}
        warning="Their patients, the patients' cases and PDFs, and their credit account are erased for good. Master data they added stays, handed to another super admin."
        onErase={async (confirmation) => {
          const result = await erase.mutateAsync({ healerId: healer.id, confirmation });
          queryClient.invalidateQueries({ queryKey: ["admin-accounts"] });
          queryClient.invalidateQueries({ queryKey: ["admin-healers"] });
          if (result.creditAccountErased) toast.success(`${healer.fullName} has been erased`);
          else toast.warning(`${healer.fullName} was erased, but their credit account must be removed by hand`);
          onClose();
        }}
      />
    );
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>Erase a healer</DialogTitle>
        <DialogDescription>
          Permanently removes a healer, their patients and cases, and their credit account. To only stop someone
          signing in, deactivate them on Healer Registration instead.
        </DialogDescription>
        <SingleSelect
          value={healerId}
          onChange={setHealerId}
          options={options}
          placeholder={isLoading ? "Loading healers…" : "Select a healer"}
          aria-label="Healer to erase"
        />
        <div className="flex justify-end gap-3 mt-4">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="destructive" disabled={!healer} onClick={() => setConfirming(true)}>
            Continue…
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
