import { useState } from "react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  useActivePolicyVersions,
  usePublishPolicyVersion,
  CONSENT_TYPE_LABELS,
  type ConsentType,
  type PolicyVersionResponse,
} from "../../hooks/admin/useLegalDocuments";

// ── Shared modal shell (copied from MasterDataPage.tsx's ModalShell) ────────────

function ModalShell({
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
      <DialogContent className="w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 gap-0 max-h-[90vh] overflow-y-auto max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        <DialogTitle className="text-lg font-bold text-text mb-4">{title}</DialogTitle>
        {children}
      </DialogContent>
    </Dialog>
  );
}

// ── Publish modal ────────────────────────────────────────────────────────────

type PublishMode = "paste" | "upload";

function PublishModal({
  consentType,
  onClose,
}: {
  consentType: ConsentType;
  onClose: () => void;
}) {
  const [mode, setMode] = useState<PublishMode>("paste");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const publish = usePublishPolicyVersion();

  const switchMode = (next: PublishMode) => {
    setMode(next);
    setText("");
    setFile(null);
    setError("");
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const hasText = text.trim().length > 0;
    const hasFile = !!file;
    if (hasText && hasFile) {
      setError("Provide either pasted text or a file, not both.");
      return;
    }
    setError("");
    try {
      await publish.mutateAsync({
        consentType,
        content: hasText ? text : undefined,
        file: hasFile ? file : undefined,
      });
      onClose();
    } catch {
      // error toast already shown by the hook
    }
  };

  return (
    <ModalShell title={`Publish New Version — ${CONSENT_TYPE_LABELS[consentType]}`} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex gap-2">
          {(["paste", "upload"] as const).map((m) => (
            <Button
              key={m}
              type="button"
              variant="outline"
              onClick={() => switchMode(m)}
              className={cn(
                "flex-1 py-2 h-auto font-medium",
                mode === m
                  ? "bg-primary/10 border-primary text-primary hover:bg-primary/10 hover:text-primary"
                  : "text-text-light"
              )}
            >
              {m === "paste" ? "Paste Text" : "Upload File"}
            </Button>
          ))}
        </div>

        {mode === "paste" ? (
          <div>
            <Label className="block text-xs text-text-light mb-1">
              Document content (Markdown)
            </Label>
            <Textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              className="border px-3 py-2.5 min-h-40"
            />
          </div>
        ) : (
          <div>
            <Label className="block text-xs text-text-light mb-1">
              Upload a Markdown or text file (.md, .txt)
            </Label>
            <Input
              type="file"
              accept=".md,.txt"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </div>
        )}

        {error && <p className="text-red-500 text-sm">{error}</p>}

        <div className="flex gap-2 pt-1">
          <Button type="submit" disabled={publish.isPending} className="flex-1 py-2 h-auto">
            {publish.isPending ? "Publishing…" : "Publish Version"}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            className="px-4 py-2 h-auto font-normal text-text"
          >
            Cancel
          </Button>
        </div>
      </form>
    </ModalShell>
  );
}

// ── Active version card / empty state ────────────────────────────────────────

function ActiveVersionCard({ entry }: { entry: PolicyVersionResponse }) {
  const publishedDate = new Date(entry.effectiveDate).toLocaleDateString();
  const publisher = entry.publisherId ?? "unknown";
  return (
    <div className="bg-card border border-border rounded-lg p-4">
      <div className="flex items-center gap-2 mb-2">
        <Badge className="font-semibold px-2 bg-primary/10 text-primary">Active</Badge>
      </div>
      <p className="text-sm text-text mb-3">
        Version {entry.policyVersion} — published {publishedDate} by {publisher}
      </p>
      <pre className="text-xs text-text-light whitespace-pre-wrap max-h-64 overflow-y-auto border border-border rounded-lg p-3 bg-background">
        {entry.content}
      </pre>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="bg-card border border-border rounded-lg p-4">
      <p className="text-sm font-semibold text-text mb-1">No version published yet</p>
      <p className="text-sm text-text-light">
        This document type has no active version. Publish one to make it available.
      </p>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

const CONSENT_TYPES: ConsentType[] = [
  "TERMS_AND_CONDITIONS",
  "PRIVACY_POLICY",
  "MEDICAL_DISCLAIMER",
  "PRACTITIONER_AGREEMENT",
];

export default function LegalDocumentsPage() {
  const [activeTab, setActiveTab] = useState<ConsentType>("TERMS_AND_CONDITIONS");
  const [showPublish, setShowPublish] = useState(false);
  const { data, isLoading, isError } = useActivePolicyVersions();

  const entry = data?.find((d) => d.consentType === activeTab) ?? null;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-text">Legal Documents</h1>
        <p className="text-sm text-text-light mt-0.5">
          Publish and manage versioned policy content for Terms & Conditions, Privacy
          Policy, Medical Disclaimer, and Practitioner Agreement.
        </p>
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-border mb-6 overflow-x-auto">
        {CONSENT_TYPES.map((type) => (
          <Button
            key={type}
            type="button"
            variant="ghost"
            onClick={() => setActiveTab(type)}
            className={cn(
              "px-4 py-2.5 h-auto rounded-none whitespace-nowrap border-b-2 -mb-px hover:bg-transparent",
              activeTab === type
                ? "border-b-primary text-primary hover:text-primary"
                : "border-b-transparent text-text-light hover:text-text"
            )}
          >
            {CONSENT_TYPE_LABELS[type]}
          </Button>
        ))}
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load active versions.</p>}

      {!isLoading && !isError && (
        <div className="space-y-4">
          {entry ? <ActiveVersionCard entry={entry} /> : <EmptyState />}
          <Button onClick={() => setShowPublish(true)} className="px-3 py-2 h-auto">
            Publish New Version
          </Button>
        </div>
      )}

      {showPublish && (
        <PublishModal consentType={activeTab} onClose={() => setShowPublish(false)} />
      )}
    </div>
  );
}
