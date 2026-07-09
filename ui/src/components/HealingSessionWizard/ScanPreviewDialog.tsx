import { X } from "lucide-react";

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
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-5xl max-h-[90vh] overflow-hidden rounded-xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b p-4">
          <h2 className="text-lg font-semibold">Recorded Scan Preview</h2>

          <button
            onClick={onClose}
            className="rounded px-2 py-1 hover:bg-gray-100"
          >
            <X className="w-4 h-4"/>
          </button>
        </div>

        <div className="overflow-auto p-4">{children}</div>
      </div>
    </div>
  );
}
