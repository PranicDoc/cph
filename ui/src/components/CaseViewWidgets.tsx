import { useState } from "react";
import { FileText, Download } from "lucide-react";
import api from "../lib/axios";
import { Medication } from "../interfaces";
import type { Protocol } from "../hooks/admin/useMasterData";
import { isLegacyProtocol } from "../lib/protocolSelection";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

function Popup({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-96 max-h-[80vh] overflow-hidden flex flex-col gap-0 p-0">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <DialogTitle className="font-semibold text-text-dark text-sm">
            {title}
          </DialogTitle>
        </div>
        <div className="px-5 py-4 overflow-y-auto">{children}</div>
      </DialogContent>
    </Dialog>
  );
}

function keyToFilename(key: string) {
  return key.split("/").pop() ?? key;
}

function LabFindingsPopup({
  keys,
  onClose,
}: {
  keys: string[];
  onClose: () => void;
}) {
  const [loading, setLoading] = useState<string | null>(null);

  const openFile = async (key: string) => {
    setLoading(key);
    try {
      const { data } = await api.get("/api/v1/cases/download", {
        params: { fileUrl: key },
      });
      window.open(data.data.url, "_blank", "noopener,noreferrer");
    } finally {
      setLoading(null);
    }
  };

  return (
    <Popup title={`Lab Findings (${keys.length})`} onClose={onClose}>
      <ul className="space-y-2">
        {keys.map((key) => (
          <li
            key={key}
            className="flex items-center justify-between gap-3 p-3 border border-border rounded-lg"
          >
            <div className="flex items-center gap-2 min-w-0">
              <FileText className="w-4 h-4 text-primary flex-shrink-0" />
              <span className="text-sm text-text truncate">
                {keyToFilename(key)}
              </span>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="xs"
              onClick={() => openFile(key)}
              disabled={loading === key}
              className="h-auto p-0 gap-1.5 text-xs font-medium text-primary hover:text-primary-dark hover:bg-transparent flex-shrink-0"
            >
              <Download className="w-3.5 h-3.5" />
              {loading === key ? "Opening..." : "Download"}
            </Button>
          </li>
        ))}
      </ul>
    </Popup>
  );
}

export function LabFindingsView({ keys }: { keys: string[] }) {
  const [open, setOpen] = useState(false);

  if (!keys || keys.length === 0)
    return <span className="text-sm text-text-light">—</span>;

  return (
    <>
      <Button
        type="button"
        variant="link"
        onClick={() => setOpen(true)}
        className="h-auto p-0 text-sm font-normal underline underline-offset-2 decoration-primary/40 hover:decoration-primary"
      >
        Click to view
      </Button>

      {open && <LabFindingsPopup keys={keys} onClose={() => setOpen(false)} />}
    </>
  );
}

function MedicationsPopup({
  medications,
  onClose,
}: {
  medications: Medication[];
  onClose: () => void;
}) {
  return (
    <Popup title={`Medications (${medications.length})`} onClose={onClose}>
      <ul className="space-y-3">
        {medications.map((med, idx) => {
          const timing = [
            med.morningDose && "Morning",
            med.afternoonDose && "Afternoon",
            med.nightDose && "Night",
          ]
            .filter(Boolean)
            .join(", ");

          return (
            <li
              key={idx}
              className="p-3 border border-border rounded-lg space-y-1.5"
            >
              <p className="font-semibold text-sm text-text-dark">
                {med.medicationName}
              </p>
              {med.dosage && (
                <p className="text-xs text-text-light">
                  Dosage: <span className="text-text">{med.dosage}</span>
                </p>
              )}
              {timing && (
                <p className="text-xs text-text-light">
                  When: <span className="text-text">{timing}</span>
                </p>
              )}
            </li>
          );
        })}
      </ul>
    </Popup>
  );
}

export function MedicationsView({
  medications,
}: {
  medications: Medication[];
}) {
  const [open, setOpen] = useState(false);

  if (!medications || medications.length === 0)
    return <span className="text-sm text-text-light">—</span>;

  return (
    <>
      <Button
        type="button"
        variant="link"
        onClick={() => setOpen(true)}
        className="h-auto p-0 text-sm font-normal underline underline-offset-2 decoration-primary/40 hover:decoration-primary"
      >
        Click to view
      </Button>

      {open && (
        <MedicationsPopup
          medications={medications}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}

function ProtocolsPopup({ protocols, onClose }: { protocols: Protocol[]; onClose: () => void }) {
  return (
    <Popup title={`Protocols (${protocols.length})`} onClose={onClose}>
      <ul className="space-y-3">
        {protocols.map((p) => (
          <li key={p.id ?? p.name} className="p-3 border border-border rounded-lg space-y-1.5">
            <p className="font-semibold text-sm text-text-dark">{p.name}</p>
            {!isLegacyProtocol(p) && p.sourceBook && (
              <p className="text-xs text-text-light">
                Book: <span className="text-text">{p.sourceBook}</span>
              </p>
            )}
            {!isLegacyProtocol(p) && p.pageNumber ? (
              <p className="text-xs text-text-light">
                Page: <span className="text-text">{p.pageNumber}</span>
              </p>
            ) : null}
          </li>
        ))}
      </ul>
    </Popup>
  );
}

function DetailedProtocolPopup({ text, onClose }: { text: string; onClose: () => void }) {
  return (
    <Popup title="Protocol" onClose={onClose}>
      <p className="text-sm text-text whitespace-pre-wrap break-words">{text}</p>
    </Popup>
  );
}

/**
 * The case's protocols. One is shown as is; several open in a popup, like lab findings and medications.
 * When the healer wrote out the protocol (`detailed`), a "View protocol" button opens it in full.
 */
export function ProtocolsView({
  protocols,
  fallback,
  detailed,
}: {
  protocols: Protocol[];
  fallback?: string;
  detailed?: string | null;
}) {
  const [listOpen, setListOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const detailedText = (detailed ?? "").trim();
  const linkClass =
    "h-auto p-0 text-sm font-normal underline underline-offset-2 decoration-primary/40 hover:decoration-primary";

  if ((!protocols || protocols.length === 0) && !detailedText) {
    return <span className="text-sm text-text-light">{fallback || "—"}</span>;
  }

  return (
    <div className="flex flex-col items-start gap-1.5">
      {protocols.length === 1 && <span className="text-sm text-text">{protocols[0].name}</span>}
      {protocols.length === 0 && fallback && <span className="text-sm text-text">{fallback}</span>}
      {protocols.length > 1 && (
        <Button type="button" variant="link" onClick={() => setListOpen(true)} className={linkClass}>
          {protocols.length} protocols · Click to view
        </Button>
      )}
      {detailedText && (
        <Button type="button" variant="link" onClick={() => setDetailOpen(true)} className={linkClass}>
          View protocol
        </Button>
      )}
      {listOpen && <ProtocolsPopup protocols={protocols} onClose={() => setListOpen(false)} />}
      {detailOpen && <DetailedProtocolPopup text={detailedText} onClose={() => setDetailOpen(false)} />}
    </div>
  );
}
