import { useState } from "react";
import { Check } from "lucide-react";

import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import { cn } from "../../lib/utils";
import type { PolicyVersionResponse } from "../../hooks/admin/useLegalDocuments";
import { DocumentReader } from "./DocumentReader";

/**
 * D-01: the practitioner-facing tab/list menu presenting all four mandatory
 * documents in any order, mirroring the admin `LegalDocumentsPage.tsx` tab
 * bar's interaction pattern and class stack.
 */
export interface DocumentTabsProps {
  documents: PolicyVersionResponse[];
  labels?: Record<string, string>; // consentType key -> display label
  agreedConsentTypes?: string[]; // optional caller-owned agreed set (Phase 34)
  onAgree: (documentId: string, version: number) => void;
}

export function DocumentTabs({
  documents,
  labels,
  agreedConsentTypes,
  onAgree,
}: DocumentTabsProps) {
  const [activeKey, setActiveKey] = useState<string | null>(null);
  const [locallyAgreed, setLocallyAgreed] = useState<string[]>([]);

  // First document selected by default without an initialising effect.
  const effectiveKey = activeKey ?? documents[0]?.consentType ?? null;
  const activeDocument = documents.find((d) => d.consentType === effectiveKey) ?? null;

  // Per D-01's "checked off as read" state and CONTEXT.md's discretion note,
  // the checkmark is driven by the `onAgree` callback firing locally, not by
  // backend tracking state — the simplest thing to keep in sync. A caller
  // (Phase 34) may seed `agreedConsentTypes`, but this component maintains
  // its own copy too so it stays useful standalone.
  const agreedSet = new Set([...(agreedConsentTypes ?? []), ...locallyAgreed]);

  const handleAgree = (documentId: string, version: number) => {
    const key = activeDocument?.consentType;
    if (key) {
      setLocallyAgreed((prev) => (prev.includes(key) ? prev : [...prev, key]));
    }
    onAgree(documentId, version);
  };

  if (documents.length === 0) {
    return (
      <div className="bg-card border border-border rounded-lg p-4">
        <p className="text-sm text-text-light">No documents to display.</p>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between border-b border-border mb-6">
        <div className="flex overflow-x-auto" role="tablist">
          {documents.map((doc) => {
            const label = labels?.[doc.consentType] ?? doc.consentType;
            const isActive = effectiveKey === doc.consentType;
            const isAgreed = agreedSet.has(doc.consentType);
            return (
              <Button
                key={doc.consentType}
                type="button"
                variant="ghost"
                role="tab"
                aria-selected={isActive}
                data-testid={"tab-" + doc.consentType}
                onClick={() => setActiveKey(doc.consentType)}
                className={cn(
                  "px-4 py-2.5 h-auto rounded-none whitespace-nowrap border-b-2 -mb-px hover:bg-transparent gap-2",
                  isActive
                    ? "border-b-primary text-primary hover:text-primary"
                    : "border-b-transparent text-text-light hover:text-text"
                )}
              >
                <span>{label}</span>
                {isAgreed && (
                  <>
                    <Check className="size-3.5" aria-hidden="true" />
                    <Badge variant="neutral">Agreed</Badge>
                  </>
                )}
              </Button>
            );
          })}
        </div>
      </div>

      {/*
        Load-bearing: only the selected document's DocumentReader is ever
        mounted here — never all four — because each reader owns its own
        reading session and scroll-gate state, and simultaneous mounting
        would let one document's recorded duration absorb time spent
        reading another (33-RESEARCH.md Pitfall 3).

        The explicit key={activeDocument.consentType} forces React to
        unmount and remount rather than reuse the instance across a tab
        switch, which is what fires the previous reader's close-tracking
        cleanup and opens a fresh session for the newly entered document.
      */}
      {activeDocument && (
        <DocumentReader
          key={activeDocument.consentType}
          document={activeDocument}
          label={labels?.[activeDocument.consentType] ?? activeDocument.consentType}
          agreed={agreedSet.has(activeDocument.consentType)}
          onAgree={handleAgree}
        />
      )}
    </div>
  );
}
