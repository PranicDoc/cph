import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { PlanPicker } from "./PlanPicker";
import { useUiVersion } from "../ui-version";

interface UpgradePlanDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  ownerId: string;
}

/** The "Upgrade plan" dialog opened from the profile page and the profile menu. */
export function UpgradePlanDialog({ open, onOpenChange, ownerId }: UpgradePlanDialogProps) {
  const { isNewView } = useUiVersion();
  const close = () => onOpenChange(false);

  if (isNewView) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent
          showCloseButton={false}
          className="w-[min(900px,calc(100%-32px))] sm:max-w-[900px] gap-0 p-0 overflow-hidden max-h-[92vh] flex flex-col max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:w-full max-sm:max-w-full max-sm:rounded-b-none"
        >
          <DialogTitle className="sr-only">Choose a plan</DialogTitle>
          <div className="hlx" style={{ display: "flex", flexDirection: "column", minHeight: 0, flex: 1 }}>
            <PlanPicker ownerId={ownerId} ownerType="HEALER" onComplete={close} onClose={close} />
          </div>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full sm:max-w-3xl gap-0 rounded-t-2xl sm:rounded-2xl max-h-[92vh] flex flex-col p-0 max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        <DialogTitle className="sr-only">Upgrade Plan</DialogTitle>
        <div className="sm:hidden flex justify-center pt-3 pb-1 shrink-0">
          <div className="w-10 h-1 bg-border rounded-full" />
        </div>
        <div className="flex-1 min-h-0 overflow-y-auto p-5 sm:p-8">
          <PlanPicker ownerId={ownerId} ownerType="HEALER" onComplete={close} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
