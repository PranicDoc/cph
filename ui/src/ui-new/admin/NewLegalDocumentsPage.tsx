import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { Plus, X } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useAuth } from "../../context/AuthContext";
import {
  useActivePolicyVersions,
  useConsentTypes,
  useCreateConsentType,
  usePublishPolicyVersion,
  useToggleConsentTypeActive,
  useToggleConsentTypeMandatory,
  type ConsentType,
  type ConsentTypeResponse,
  type PolicyVersionResponse,
} from "../../hooks/admin/useLegalDocuments";
import { HlField, HlSwitch } from "../components/Controls";
import { shortDate } from "../lib/format";

function Sheet({ title, description, onClose, children }: { title: string; description?: string; onClose: () => void; children: ReactNode }) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent showCloseButton={false} className="hl-cdialog">
        <div className="hlx" style={{ gap: 0 }}>
          <header style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", padding: "22px 24px 14px" }}>
            <div>
              <DialogTitle asChild>
                <h2>{title}</h2>
              </DialogTitle>
              {description && (
                <DialogDescription asChild>
                  <p className="muted" style={{ marginTop: 4 }}>{description}</p>
                </DialogDescription>
              )}
            </div>
            <button className="icon-btn" type="button" aria-label="Close" onClick={onClose}>
              <X className="ico" />
            </button>
          </header>
          <div style={{ padding: "0 24px 22px" }}>{children}</div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function PublishModal({ consentType, label, onClose }: { consentType: ConsentType; label: string; onClose: () => void }) {
  const [mode, setMode] = useState<"paste" | "upload">("paste");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const publish = usePublishPolicyVersion();

  const switchMode = (next: "paste" | "upload") => {
    setMode(next);
    setText("");
    setFile(null);
    setError("");
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const hasText = text.trim().length > 0;
    if (hasText && file) {
      setError("Provide either pasted text or a file, not both.");
      return;
    }
    setError("");
    try {
      await publish.mutateAsync({ consentType, content: hasText ? text : undefined, file: file ?? undefined });
      onClose();
    } catch {
      // error toast already shown by the hook
    }
  };

  return (
    <Sheet title={`Publish new version`} description={label} onClose={onClose}>
      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
        <div className="seg" role="group" aria-label="Source" style={{ alignSelf: "flex-start" }}>
          <button type="button" className={mode === "paste" ? "on" : ""} aria-pressed={mode === "paste"} onClick={() => switchMode("paste")}>Paste text</button>
          <button type="button" className={mode === "upload" ? "on" : ""} aria-pressed={mode === "upload"} onClick={() => switchMode("upload")}>Upload file</button>
        </div>
        {mode === "paste" ? (
          <HlField label="Document content (Markdown)" htmlFor="pv-text">
            <textarea id="pv-text" className="input area" style={{ minHeight: 180 }} value={text} onChange={(e) => setText(e.target.value)} />
          </HlField>
        ) : (
          <HlField label="Upload a Markdown or text file (.md, .txt)" htmlFor="pv-file">
            <input id="pv-file" className="input" type="file" accept=".md,.txt" style={{ paddingTop: 8 }} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </HlField>
        )}
        {error && <div className="err">{error}</div>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
          <button className="btn btn-secondary" type="button" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" type="submit" disabled={publish.isPending}>{publish.isPending ? "Publishing…" : "Publish version"}</button>
        </div>
      </form>
    </Sheet>
  );
}

function AddTypeModal({ onClose, onCreated }: { onClose: () => void; onCreated: (key: string) => void }) {
  const [name, setName] = useState("");
  const create = useCreateConsentType();
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const created = await create.mutateAsync({ label: name });
      onCreated(created.key);
      onClose();
    } catch {
      // error toast already shown by the hook; keep the modal open with the text intact
    }
  };
  return (
    <Sheet title="Add consent form" onClose={onClose}>
      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
        <HlField label="Form name" htmlFor="ct-name" help="The internal identifier is generated from the name automatically.">
          <input id="ct-name" className="input" autoFocus placeholder="e.g. Photo Release Consent" value={name} onChange={(e) => setName(e.target.value)} />
        </HlField>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
          <button className="btn btn-secondary" type="button" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" type="submit" disabled={create.isPending || !name.trim()}>{create.isPending ? "Creating…" : "Add consent form"}</button>
        </div>
      </form>
    </Sheet>
  );
}

function TypeActions({ type, isSuperAdmin, onDeactivated }: { type: ConsentTypeResponse; isSuperAdmin: boolean; onDeactivated: () => void }) {
  const toggleMandatory = useToggleConsentTypeMandatory();
  const toggleActive = useToggleConsentTypeActive();
  const [confirm, setConfirm] = useState(false);
  const canManage = !type.systemDefined || isSuperAdmin;
  const box = { display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, flexWrap: "wrap" as const, padding: "14px 16px", borderRadius: 12, background: "var(--primary-soft)" };

  if (!type.active) {
    return (
      <div style={box}>
        <div>
          <div style={{ fontWeight: 600 }}>This consent form is deactivated</div>
          <div className="small muted">Its published versions and healers' recorded acceptances were preserved while it was inactive.</div>
        </div>
        <button className="btn btn-primary" type="button" disabled={toggleActive.isPending} onClick={() => toggleActive.mutate({ id: type.id, value: true })}>
          Reactivate
        </button>
      </div>
    );
  }
  if (!canManage) return null;
  return (
    <div style={box}>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <HlSwitch label="Required at registration" checked={type.mandatory} disabled={toggleMandatory.isPending} onChange={(v) => toggleMandatory.mutate({ id: type.id, value: v })} />
        <div>
          <div style={{ fontWeight: 600 }}>Required at registration</div>
          {type.systemDefined && (
            <div className="small muted">This is one of the four original documents that gates account registration (REG-02). This control is visible to super admins only.</div>
          )}
        </div>
      </div>
      <button className="btn btn-danger" type="button" onClick={() => setConfirm(true)}>Deactivate</button>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="Deactivate this consent form?"
        description={`"${type.label}" will disappear from the default tab bar and can no longer accept a new published version. Its published versions and healers' recorded acceptances are kept, and you can reactivate it any time via "Show inactive".`}
        confirmLabel="Confirm Deactivate"
        cancelLabel="Keep Active"
        destructive
        loading={toggleActive.isPending}
        onConfirm={() => toggleActive.mutate({ id: type.id, value: false }, { onSuccess: onDeactivated, onSettled: () => setConfirm(false) })}
      />
    </div>
  );
}

/** New-view Legal documents: same consent-type rules and publishing as the classic page, laid out as in the mockup. */
export default function NewLegalDocumentsPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "SUPADM";
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [showPublish, setShowPublish] = useState(false);
  const [showAddType, setShowAddType] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const { data: types, isLoading: typesLoading, isError: typesError } = useConsentTypes(showInactive);
  const { data, isLoading: versionsLoading, isError: versionsError } = useActivePolicyVersions();
  const isLoading = typesLoading || versionsLoading;
  const isError = typesError || versionsError;
  const activeKey = selectedKey ?? types?.[0]?.key ?? null;
  const activeType = types?.find((t) => t.key === activeKey) ?? null;
  const entry: PolicyVersionResponse | null = data?.find((d) => d.consentType === activeKey) ?? null;

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Legal documents</h1>
          <p className="muted" style={{ marginTop: 6 }}>Publish and manage versioned policy content for every consent form in the registry.</p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <label className="small ongr" style={{ display: "flex", alignItems: "center", gap: 8 }}>
            Show inactive
            <HlSwitch label="Show inactive" checked={showInactive} onChange={setShowInactive} />
          </label>
          <button className="btn btn-secondary" type="button" onClick={() => setShowAddType(true)}>
            <Plus className="ico" /> Add consent form
          </button>
        </div>
      </div>

      <div className="tabs glass" role="tablist" aria-label="Consent forms">
        {(types ?? []).map((t) => (
          <a
            key={t.key}
            role="tab"
            aria-selected={activeKey === t.key}
            tabIndex={0}
            className={activeKey === t.key ? "on" : ""}
            style={t.active ? undefined : { opacity: 0.55 }}
            onClick={() => setSelectedKey(t.key)}
            onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && setSelectedKey(t.key)}
          >
            {t.label}
            {!t.systemDefined && <span className="badge off" style={{ marginLeft: 8, height: 20 }}>{t.mandatory ? "Mandatory" : "Optional"}</span>}
            {!t.active && <span className="badge off" style={{ marginLeft: 6, height: 20 }}>Inactive</span>}
          </a>
        ))}
      </div>

      {isLoading && <p className="muted">Loading…</p>}
      {isError && <p className="err">Failed to load active versions.</p>}

      {!isLoading && !isError && activeType && (
        <>
          <TypeActions type={activeType} isSuperAdmin={isSuperAdmin} onDeactivated={() => !showInactive && setSelectedKey(null)} />
          <div className="grid2">
            <div className="surface card">
              <div className="card-h">
                <div>
                  <h2>{activeType.label}</h2>
                  <div className="small muted" style={{ marginTop: 4 }}>
                    {entry ? `Published version ${entry.policyVersion} · ${shortDate(entry.effectiveDate)} by ${entry.publisherName ?? "Unknown"}` : "No version published yet"}
                  </div>
                </div>
                {activeType.active && (
                  <button className="btn btn-primary" type="button" onClick={() => setShowPublish(true)}>
                    <Plus className="ico" /> Publish new version
                  </button>
                )}
              </div>
              {entry ? (
                <pre className="input area" style={{ maxHeight: 320, overflow: "auto", whiteSpace: "pre-wrap", lineHeight: 1.6, color: "var(--muted)", fontFamily: "inherit" }}>{entry.content}</pre>
              ) : (
                <p className="muted">This document type has no active version. Publish one to make it available.</p>
              )}
            </div>
            <div className="surface card">
              <div className="card-h"><h2>Versions</h2></div>
              {entry ? (
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{entry.policyVersion}</div>
                    <div className="small muted num">{shortDate(entry.effectiveDate)}</div>
                  </div>
                  <span className="badge ok">Active</span>
                </div>
              ) : (
                <p className="muted">Nothing published yet.</p>
              )}
            </div>
          </div>
        </>
      )}

      {showPublish && activeType && activeType.active && <PublishModal consentType={activeType.key} label={activeType.label} onClose={() => setShowPublish(false)} />}
      {showAddType && <AddTypeModal onClose={() => setShowAddType(false)} onCreated={(key) => setSelectedKey(key)} />}
    </div>
  );
}
