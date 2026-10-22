import { useState } from "react";
import { versioned } from "../../ui-version";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { cn } from "@/lib/utils";
import { useAuth } from "../../context/AuthContext";
import {
  useActivePolicyVersions,
  usePublishPolicyVersion,
  useConsentTypes,
  useCreateConsentType,
  useToggleConsentTypeMandatory,
  useToggleConsentTypeActive,
  type ConsentType,
  type ConsentTypeResponse,
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
  label,
  onClose,
}: {
  consentType: ConsentType;
  label: string;
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
    <ModalShell title={`Publish New Version — ${label}`} onClose={onClose}>
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
  const publisher = entry.publisherName ?? "Unknown";
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

// ── Per-type action row (D-04, D-07) ────────────────────────────────────────────

function TypeActionRow({
  type,
  isSuperAdmin,
  onDeactivated,
}: {
  type: ConsentTypeResponse;
  isSuperAdmin: boolean;
  onDeactivated: () => void;
}) {
  const toggleMandatory = useToggleConsentTypeMandatory();
  const toggleActive = useToggleConsentTypeActive();
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);

  const canManage = !type.systemDefined || isSuperAdmin;

  // Deactivated types (D-08): only a Reactivate action, no mandatory
  // toggle and no Deactivate — publishing against them is refused
  // server-side (T-31.1-02), so nothing here offers it either.
  if (!type.active) {
    return (
      <div className="mb-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 rounded-lg border border-border bg-card px-4 py-3">
        <div>
          <p className="text-sm font-semibold text-text">This consent form is deactivated</p>
          <p className="text-xs text-text-light mt-0.5">
            Its published versions and healers&apos; recorded acceptances were preserved
            while it was inactive.
          </p>
        </div>
        <Button
          type="button"
          size="sm"
          onClick={() => toggleActive.mutate({ id: type.id, value: true })}
          disabled={toggleActive.isPending}
          className="shrink-0"
        >
          Reactivate
        </Button>
      </div>
    );
  }

  if (!canManage) return null;

  return (
    <div className="mb-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 rounded-lg border border-border bg-card px-4 py-3">
      <div className="flex items-center gap-3">
        <Switch
          checked={type.mandatory}
          onCheckedChange={(checked) =>
            toggleMandatory.mutate({ id: type.id, value: checked })
          }
          disabled={toggleMandatory.isPending}
        />
        <div>
          <Label className="text-sm text-text">Required at registration</Label>
          {type.systemDefined && (
            <p className="text-xs text-text-light mt-0.5">
              This is one of the four original documents that gates account registration
              (REG-02). This control is visible to super admins only.
            </p>
          )}
        </div>
      </div>

      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => setConfirmDeactivate(true)}
        className="text-danger hover:text-danger shrink-0"
      >
        Deactivate
      </Button>

      <ConfirmDialog
        open={confirmDeactivate}
        onOpenChange={setConfirmDeactivate}
        title="Deactivate this consent form?"
        description={`"${type.label}" will disappear from the default tab bar and can no longer accept a new published version. Its published versions and healers' recorded acceptances are kept, and you can reactivate it any time via "Show inactive".`}
        confirmLabel="Confirm Deactivate"
        cancelLabel="Keep Active"
        destructive
        loading={toggleActive.isPending}
        onConfirm={() => {
          toggleActive.mutate(
            { id: type.id, value: false },
            {
              onSuccess: onDeactivated,
              onSettled: () => setConfirmDeactivate(false),
            }
          );
        }}
      />
    </div>
  );
}

// ── Add Consent Form modal (D-03) ───────────────────────────────────────────────

function AddConsentTypeModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (key: string) => void;
}) {
  const [name, setName] = useState("");
  const create = useCreateConsentType();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const created = await create.mutateAsync({ label: name });
      onCreated(created.key);
      onClose();
    } catch {
      // error toast already shown by the hook; keep modal open with the entered text intact
    }
  };

  return (
    <ModalShell title="Add Consent Form" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <Label className="block text-xs text-text-light mb-1">Form name</Label>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Photo Release Consent"
            className="border px-3 py-2.5"
            autoFocus
          />
          <p className="text-xs text-text-light mt-1">
            The internal identifier is generated from the name automatically.
          </p>
        </div>

        <div className="flex gap-2 pt-1">
          <Button
            type="submit"
            disabled={create.isPending || !name.trim()}
            className="flex-1 py-2 h-auto"
          >
            {create.isPending ? "Creating…" : "Add Consent Form"}
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

// ── Page ──────────────────────────────────────────────────────────────────────

function ClassicLegalDocumentsPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "SUPADM";

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [showPublish, setShowPublish] = useState(false);
  const [showAddType, setShowAddType] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const {
    data: types,
    isLoading: typesLoading,
    isError: typesError,
  } = useConsentTypes(showInactive);
  const { data, isLoading: versionsLoading, isError: versionsError } = useActivePolicyVersions();

  const isLoading = typesLoading || versionsLoading;
  const isError = typesError || versionsError;

  const activeKey = selectedKey ?? types?.[0]?.key ?? null;
  const activeConsentType = types?.find((t) => t.key === activeKey) ?? null;
  const entry = data?.find((d) => d.consentType === activeKey) ?? null;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-text">Legal Documents</h1>
        <p className="text-sm text-text-light mt-0.5">
          Publish and manage versioned policy content for every consent form in the
          registry.
        </p>
      </div>

      {/* Tab bar */}
      <div className="flex items-center justify-between border-b border-border mb-6">
        <div className="flex overflow-x-auto">
          {(types ?? []).map((type) => (
            <Button
              key={type.key}
              type="button"
              variant="ghost"
              onClick={() => setSelectedKey(type.key)}
              className={cn(
                "px-4 py-2.5 h-auto rounded-none whitespace-nowrap border-b-2 -mb-px hover:bg-transparent gap-2",
                activeKey === type.key
                  ? "border-b-primary text-primary hover:text-primary"
                  : "border-b-transparent text-text-light hover:text-text",
                !type.active && "opacity-50"
              )}
            >
              <span>{type.label}</span>
              {!type.systemDefined && (
                <Badge variant="neutral" className="font-medium">
                  {type.mandatory ? "Mandatory" : "Optional"}
                </Badge>
              )}
              {!type.active && (
                <Badge variant="neutral" className="font-medium">
                  Inactive
                </Badge>
              )}
            </Button>
          ))}
        </div>
        <div className="flex items-center gap-3 shrink-0 ml-2 mb-1">
          <label className="flex items-center gap-2 text-xs font-medium text-text-light cursor-pointer select-none">
            <Switch size="sm" checked={showInactive} onCheckedChange={setShowInactive} />
            Show inactive
          </label>
          <Button
            type="button"
            variant="outline"
            onClick={() => setShowAddType(true)}
            className="px-3 py-1.5 h-auto text-sm font-medium"
          >
            Add Consent Form
          </Button>
        </div>
      </div>

      {!isLoading && !isError && activeConsentType && (
        <TypeActionRow
          type={activeConsentType}
          isSuperAdmin={isSuperAdmin}
          onDeactivated={() => {
            if (!showInactive) setSelectedKey(null);
          }}
        />
      )}

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load active versions.</p>}

      {!isLoading && !isError && activeConsentType && (
        <div className="space-y-4">
          {entry ? <ActiveVersionCard entry={entry} /> : <EmptyState />}
          {activeConsentType.active && (
            <Button onClick={() => setShowPublish(true)} className="px-3 py-2 h-auto">
              Publish New Version
            </Button>
          )}
        </div>
      )}

      {showPublish && activeConsentType && activeConsentType.active && (
        <PublishModal
          consentType={activeConsentType.key}
          label={activeConsentType.label}
          onClose={() => setShowPublish(false)}
        />
      )}

      {showAddType && (
        <AddConsentTypeModal
          onClose={() => setShowAddType(false)}
          onCreated={(key) => setSelectedKey(key)}
        />
      )}
    </div>
  );
}

const LegalDocumentsPage = versioned(ClassicLegalDocumentsPage, () => import("../../ui-new/admin/NewLegalDocumentsPage"));
export default LegalDocumentsPage;
