import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { ReviewComment } from "./reviewApi";
import { buildThreads } from "./threads";

interface Props {
  comments: ReviewComment[];
  /** Threads on an older revision are labelled "on version N". */
  latestRevisionNo: number;
  activeThreadId?: string | null;
  onSelectThread?: (id: string) => void;
  /** Every side can reply. */
  onReply: (parentId: string, body: string) => Promise<unknown>;
  /** Instructor side only: shows a Resolve button on open threads. */
  onResolve?: (commentId: string) => void;
  /** Instructor side only: a general (unpinned) comment. */
  onAddGeneral?: (body: string) => Promise<unknown>;
  /** A pin placed on the PDF that is waiting for its comment text. */
  draftPin?: { page: number } | null;
  onSubmitDraft?: (body: string) => Promise<unknown>;
  onCancelDraft?: () => void;
  error?: string | null;
}

function Composer({
  label,
  submitLabel,
  onSubmit,
  onCancel,
  autoFocus,
}: {
  label: string;
  submitLabel: string;
  onSubmit: (body: string) => Promise<unknown>;
  onCancel?: () => void;
  autoFocus?: boolean;
}) {
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    const text = body.trim();
    if (!text || busy) return;
    setBusy(true);
    try {
      await onSubmit(text);
      setBody("");
    } catch {
      // The parent shows the error; keep the text so nothing typed is lost.
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-2">
      <Textarea
        aria-label={label}
        placeholder={label}
        value={body}
        maxLength={4000}
        autoFocus={autoFocus}
        onChange={(e) => setBody(e.target.value)}
      />
      <div className="flex gap-2">
        <Button type="button" size="sm" disabled={busy || !body.trim()} onClick={submit}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" size="sm" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * Threads of one submission: general and pinned, oldest first. Each shows its replies, a reply box,
 * and (instructor side only) a Resolve button.
 */
export default function ThreadPanel({
  comments,
  latestRevisionNo,
  activeThreadId,
  onSelectThread,
  onReply,
  onResolve,
  onAddGeneral,
  draftPin,
  onSubmitDraft,
  onCancelDraft,
  error,
}: Props) {
  const threads = buildThreads(comments);

  return (
    <aside aria-label="Comments" className="w-full space-y-3">
      {error && (
        <p role="alert" className="rounded border border-red-300 bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {draftPin && onSubmitDraft && (
        <section className="space-y-2 rounded border border-blue-300 bg-blue-50/50 p-3">
          <h3 className="text-sm font-medium">New pin on page {draftPin.page}</h3>
          <Composer label="Describe what needs changing" submitLabel="Post pin" onSubmit={onSubmitDraft}
            onCancel={onCancelDraft} autoFocus />
        </section>
      )}

      {threads.length === 0 && !draftPin && <p className="text-sm text-muted-foreground">No comments yet.</p>}

      {threads.map(({ root, replies, pinNumber }) => {
        const resolved = root.resolvedAt != null;
        return (
          <section
            key={root.id}
            data-thread-id={root.id}
            className={
              "space-y-2 rounded border p-3 " + (root.id === activeThreadId ? "border-blue-500 " : "") +
              (resolved ? "opacity-70" : "")
            }
          >
            <header className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              {pinNumber != null ? (
                <button type="button" className="font-medium underline" onClick={() => onSelectThread?.(root.id)}>
                  Pin {pinNumber} · page {root.page}
                </button>
              ) : (
                <span className="font-medium">General</span>
              )}
              {root.revisionNo < latestRevisionNo && <span>on version {root.revisionNo}</span>}
              {resolved && <span className="rounded bg-gray-100 px-1.5 py-0.5">Resolved</span>}
            </header>

            {[root, ...replies].map((c) => (
              <div key={c.id} className="text-sm">
                <span className="font-medium">
                  {c.authorName || (c.authorSide === "REVIEWER" ? "Instructor" : "Healer")}
                </span>
                <span className="ml-1 text-xs text-muted-foreground">
                  {c.authorSide === "REVIEWER" ? "Instructor" : "Healer"}
                </span>
                <p className="whitespace-pre-wrap break-words">{c.body}</p>
              </div>
            ))}

            <Composer label="Reply" submitLabel="Reply" onSubmit={(body) => onReply(root.id, body)} />
            {onResolve && !resolved && (
              <Button type="button" size="sm" variant="outline" onClick={() => onResolve(root.id)}>
                Resolve
              </Button>
            )}
          </section>
        );
      })}

      {onAddGeneral && (
        <section className="space-y-2 rounded border border-dashed p-3">
          <h3 className="text-sm font-medium">General comment</h3>
          <Composer label="Add a general comment" submitLabel="Comment" onSubmit={onAddGeneral} />
        </section>
      )}
    </aside>
  );
}
