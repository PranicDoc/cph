import { Shield } from "lucide-react";

/** Reminds a super admin that they are acting on reviews that are not theirs. */
export default function SuperAdminBanner() {
  return (
    <div
      role="note"
      className="surface flex items-start gap-3 rounded-xl border border-primary/30 bg-primary/10 p-3 text-sm"
    >
      <Shield className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
      <p>
        <b>You are acting as super admin.</b> You can open and decide any submission. Your decisions and
        reassignments are recorded under your name in the audit log.
      </p>
    </div>
  );
}
