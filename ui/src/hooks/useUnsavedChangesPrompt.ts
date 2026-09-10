import { useEffect } from "react";
import { useBlocker, useBeforeUnload, type Blocker } from "react-router-dom";

/**
 * Shared discard-unsaved-changes guard for the role create/edit form (ROLE-08).
 *
 * Requires the app to run through a react-router v7 **data router** (see
 * `App.tsx`) — `useBlocker` throws under a declarative `<BrowserRouter>`.
 *
 * Behaviour:
 * - `useBlocker` fires only when `isDirty` AND the pathname actually changes.
 *   A pure query-string change (e.g. the roles list's `?search`) never
 *   triggers the prompt.
 * - `useBeforeUnload` covers tab close / reload / external navigation, which
 *   `useBlocker` cannot see: when dirty it calls `preventDefault()` and sets
 *   `returnValue` so the browser shows its native "Leave site?" dialog.
 * - When the form stops being dirty (e.g. after a successful save) any block
 *   still pending is reset, so the app's own post-save redirect is never
 *   intercepted (38-RESEARCH Pitfall 8).
 *
 * Returns the raw `Blocker` so the consumer can render its own confirm dialog
 * and call `blocker.proceed()` / `blocker.reset()`.
 */
export function useUnsavedChangesPrompt(isDirty: boolean): Blocker {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      isDirty && currentLocation.pathname !== nextLocation.pathname
  );

  useBeforeUnload(
    (e) => {
      if (isDirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    },
    { capture: true }
  );

  useEffect(() => {
    if (blocker.state === "blocked" && !isDirty) {
      blocker.reset?.();
    }
  }, [blocker, isDirty]);

  return blocker;
}
