import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";

interface ScanPreviewDialogProps {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
}

export function ScanPreviewDialog({
  open,
  onClose,
  children,
}: ScanPreviewDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full sm:max-w-5xl max-h-[90vh] overflow-hidden gap-0 p-0">
        <div className="flex items-center justify-between border-b p-4">
          <DialogTitle className="text-lg font-semibold">
            Recorded Scan Preview
          </DialogTitle>
        </div>

        <div className="overflow-auto p-4">{children}</div>
      </DialogContent>
    </Dialog>
  );
}
