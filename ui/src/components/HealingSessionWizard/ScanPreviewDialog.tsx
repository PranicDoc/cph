import { X } from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { useUiVersion } from "../../ui-version";

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
  const { isNewView } = useUiVersion();

  if (isNewView) {
    return (
      <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
        <DialogContent
          showCloseButton={false}
          className="w-[min(560px,calc(100%-32px))] sm:max-w-[560px] max-h-[90vh] gap-0 p-0 overflow-hidden flex flex-col max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:w-full max-sm:max-w-full max-sm:rounded-b-none"
        >
          <div className="hlx" style={{ display: "flex", flexDirection: "column", minHeight: 0, flex: 1 }}>
            <header style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 12, padding: "22px 24px 16px", borderBottom: "1px solid var(--hair)" }}>
              <div>
                <DialogTitle asChild><h2>Scan preview</h2></DialogTitle>
                <p className="muted" style={{ marginTop: 4 }}>These readings were extracted from your recording.</p>
              </div>
              <button type="button" className="icon-btn" aria-label="Close" onClick={onClose}>
                <X className="ico" />
              </button>
            </header>
            <div className="stack" style={{ flex: 1, minHeight: 0, overflow: "auto", padding: "20px 24px" }}>{children}</div>
            <footer style={{ display: "flex", justifyContent: "flex-end", gap: 10, padding: "14px 24px", borderTop: "1px solid var(--hair)" }}>
              <button type="button" className="btn btn-secondary" onClick={onClose}>Close</button>
            </footer>
          </div>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full sm:max-w-5xl max-h-[90vh] overflow-hidden gap-0 p-0">
        <div className="flex items-center justify-between border-b p-4 pr-12">
          <DialogTitle className="text-base font-semibold sm:text-lg">
            Recorded Scan Preview
          </DialogTitle>
        </div>

        <div className="overflow-auto p-2 sm:p-4">{children}</div>
      </DialogContent>
    </Dialog>
  );
}
