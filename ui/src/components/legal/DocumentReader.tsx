import { useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import { Button } from "../ui/button";
import type { PolicyVersionResponse } from "../../hooks/admin/useLegalDocuments";
import { useScrollGate } from "../../hooks/legal/useScrollGate";
import { useReadingSessionLifecycle } from "../../hooks/legal/useReadingSession";

/**
 * READ-01/READ-03/READ-04: the reusable scroll-gated document reader.
 *
 * D-02/D-03 boundary: `onClick` on the "I Agree" button calls ONLY the
 * `onAgree` prop, with the document's id and version as its sole
 * arguments. This component makes no HTTP call of its own to accept a
 * consent — no call to the consent-accept endpoint, no local "agreed"
 * persistence. The `agreed` prop (whether this document has already been
 * accepted) is owned entirely by the parent (33-07's DocumentTabs, and
 * eventually Phase 34). All reading-session HTTP (open on mount, close on
 * unmount/hidden) is delegated to `useReadingSessionLifecycle` from 33-05 —
 * this file issues no HTTP calls itself.
 */
export interface DocumentReaderProps {
  document: PolicyVersionResponse;
  label: string;
  agreed?: boolean;
  onAgree: (documentId: string, version: number) => void;
}

export function DocumentReader({
  document,
  label,
  agreed = false,
  onAgree,
}: DocumentReaderProps) {
  const { hasReachedEnd, sentinelRef } = useScrollGate();

  // Kept in sync with `hasReachedEnd` so the reading-session hook always
  // reads the latest value at close time without needing `hasReachedEnd`
  // itself in its own dependency array.
  const hasReachedEndRef = useRef(false);
  useEffect(() => {
    hasReachedEndRef.current = hasReachedEnd;
  }, [hasReachedEnd]);

  useReadingSessionLifecycle({
    consentType: document.consentType,
    policyVersion: document.policyVersion,
    hasReachedEndRef,
  });

  return (
    <div className="bg-card border border-border rounded-lg overflow-hidden flex flex-col">
      <div
        data-testid="document-version"
        className="sticky top-0 z-10 bg-card border-b border-border px-4 py-3"
      >
        <p className="text-sm font-semibold text-text">{label}</p>
        <p className="text-sm text-text-light">Version {document.policyVersion}</p>
      </div>

      <div className="max-h-[60vh] overflow-y-auto px-4 py-3">
        <div className="prose prose-sm max-w-none text-text">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {document.content ?? ""}
          </ReactMarkdown>
        </div>
        <div ref={sentinelRef} aria-hidden="true" />
      </div>

      <div className="border-t border-border px-4 py-3 flex items-center gap-3">
        <Button
          type="button"
          disabled={!hasReachedEnd || agreed}
          onClick={() => onAgree(document.id, document.policyVersion)}
        >
          I Agree
        </Button>
        {agreed && <span className="text-sm text-text-light">Agreed</span>}
        {!hasReachedEnd && (
          <span className="text-sm text-text-light">
            Scroll to the end of the document to continue.
          </span>
        )}
      </div>
    </div>
  );
}
