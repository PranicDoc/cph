import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Activity, LogOut } from "lucide-react";

import { Button } from "@/components/ui/button";
import ErrorState from "../components/ErrorState";
import ClassicLegalCenterShell from "../components/legal/LegalCenterShell";
import { versioned } from "../ui-version";
import { DocumentTabs } from "../components/legal/DocumentTabs";
import { useAuth } from "../context/AuthContext";
import {
  useOutstandingConsents,
  useAcceptConsent,
} from "../hooks/legal/useOutstandingConsents";
import { formatConsentTypeLabel } from "../lib/consentLabels";
import { extractDetail } from "../lib/utils";

// The new view has its own chrome for this screen; everything inside it is shared.
const LegalCenterShell = versioned(ClassicLegalCenterShell, () => import("../ui-new/shell/NewLegalCenterShell"));

/**
 * The `/legal-center` screen (REG-01/REG-02): mounts Phase 33's
 * `DocumentTabs`/`DocumentReader` against the real outstanding-consent
 * list and wires `onAgree` to the real accept mutation (34-01). Renders
 * four mutually exclusive states: loading, error, empty (defense-in-depth
 * only — see the comment on that branch below), and content.
 */
export default function LegalCenter() {
  const { data, isLoading, isError, error, refetch } = useOutstandingConsents();
  const accept = useAcceptConsent();
  const { logout } = useAuth();
  const navigate = useNavigate();

  const documents = data ?? [];
  const settled = !isLoading && !isError;
  const isEmpty = settled && documents.length === 0;

  useEffect(() => {
    if (isEmpty) {
      navigate("/", { replace: true });
    }
  }, [isEmpty, documents.length, navigate]);

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4">
          <Activity className="w-10 h-10 text-primary animate-pulse" />
          <p className="text-text-light font-medium">Loading...</p>
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        variant="generic"
        title="We Couldn't Check Your Account Status"
        message={
          extractDetail(error) ??
          "Something went wrong while checking your account status. Please try again."
        }
        actions={
          <>
            <Button
              variant="outline"
              onClick={() => refetch()}
              data-testid="legal-center-retry"
            >
              Retry
            </Button>
            <Button
              variant="outline"
              onClick={() => logout()}
              data-testid="legal-center-error-logout"
            >
              <LogOut className="mr-2 h-4 w-4" />
              Logout
            </Button>
          </>
        }
      />
    );
  }

  if (isEmpty) {
    // Defense-in-depth ONLY, not the D-05 mechanism. Once Plan 34-03 lands,
    // ProtectedRoute is this route's parent, reads the same
    // ["consents","outstanding"] query, and returns <Navigate to="/" replace />
    // instead of <Outlet /> the moment the list is empty — so in the
    // integrated app LegalCenter is never rendered in this state at all,
    // including on a bookmarked/direct hit to /legal-center. D-05 (auto-
    // redirect after the last acceptance) and the bookmarked-URL case are
    // both owned by the guard and are proven by Plan 34-03's
    // redirects_away_from_the_legal_center_once_nothing_is_outstanding.
    // This branch exists so the page remains self-recovering if it is ever
    // rendered standalone (as in its own unit test), or in a hypothetical
    // render-ordering race, instead of dead-ending on DocumentTabs's bare
    // "No documents to display." card with no way forward. Its unit test
    // proves the fallback, not D-05.
    return (
      <LegalCenterShell>
        <h1 className="text-xl font-semibold text-text">All caught up</h1>
        <p className="text-sm text-text-light">
          You have no outstanding documents. Redirecting you to your dashboard&hellip;
        </p>
      </LegalCenterShell>
    );
  }

  const labels = Object.fromEntries(
    documents.map((d) => [d.consentType, formatConsentTypeLabel(d.consentType)])
  );

  function handleAgree(documentId: string, version: number) {
    const doc = documents.find((d) => d.id === documentId);
    if (doc) {
      accept.mutate({ consentType: doc.consentType, policyVersion: version });
    }
  }

  return (
    <LegalCenterShell>
      <h1 className="text-2xl font-semibold text-text">Legal Center</h1>
      <p className="text-sm text-text-light">
        Please review and accept the following documents to continue using your account.
      </p>
      {/*
        Load-bearing remount key. DocumentTabs (Phase 33, frozen — not
        modifiable here) keeps the selected tab in uncontrolled internal
        state and resolves effectiveKey = activeKey ?? documents[0]?.consentType.
        If the practitioner clicks a non-default tab and then agrees to
        that document, the accepted consentType drops out of the refetched
        documents array while activeKey stays pinned to the now-absent
        value, so activeDocument resolves to null and the reader panel
        goes blank until they manually click a remaining tab. Keying the
        element on the joined consentType list remounts DocumentTabs
        exactly when the outstanding set actually changes, which resets
        activeKey to null so the first remaining document is selected
        automatically. An identical refetch produces an identical key
        string, so no remount happens on ordinary background refetches,
        and it costs no extra reading sessions because DocumentTabs
        already unmounts/remounts its child DocumentReader via its own
        key={activeDocument.consentType} whenever the displayed document
        changes. The alternative fix (making DocumentTabs controlled) is
        out of scope because Phase 33's components are frozen this phase.
      */}
      <div className="mt-4">
        <DocumentTabs
          key={documents.map((d) => d.consentType).join("|")}
          documents={documents}
          labels={labels}
          onAgree={handleAgree}
        />
      </div>
    </LegalCenterShell>
  );
}
