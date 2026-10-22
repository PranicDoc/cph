import { useUiVersion } from "../ui-version";
import { BrandMark } from "../ui-new/components/BrandMark";
import { ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  ArrowLeft,
  Mail,
  ShieldAlert,
  ShieldX,
  FileQuestion,
  AlertTriangle,
  type LucideIcon,
} from "lucide-react";
import { extractDetail } from "../lib/utils";
import { Button } from "@/components/ui/button";

export type ErrorStateVariant = "forbidden" | "expired" | "notFound" | "generic" | "inviteExpired";

interface VariantConfig {
  icon: LucideIcon;
  badge: string;
  title: string;
  message: string;
}

const VARIANTS: Record<ErrorStateVariant, VariantConfig> = {
  forbidden: {
    icon: ShieldX,
    badge: "Access Denied",
    title: "You Don't Have Permission",
    message:
      "You don't have permission to view this page. If you believe this is a mistake, contact an administrator.",
  },
  expired: {
    icon: ShieldAlert,
    badge: "Link Expired or Already Used",
    title: "Feedback Link Unavailable",
    message:
      "This feedback link has either expired or your feedback has already been submitted. If you believe this is an error, please contact your healer directly to request a new link.",
  },
  notFound: {
    icon: FileQuestion,
    badge: "Page Not Found",
    title: "We Couldn't Find That Page",
    message: "The page you're looking for doesn't exist or may have been moved.",
  },
  generic: {
    icon: AlertTriangle,
    badge: "Something Went Wrong",
    title: "Unexpected Error",
    message: "Something went wrong. Please try again, or contact support if the problem persists.",
  },
  inviteExpired: {
    icon: ShieldAlert,
    badge: "Invite Link Unavailable",
    title: "This Invite Link No Longer Works",
    message:
      "This invite link has either expired or already been used. Please contact your clinic admin to request a new invite.",
  },
};

function BackButton() {
  return (
    <Button
      variant="outline"
      onClick={() => window.history.back()}
      className="px-4 py-2 h-auto shadow-sm text-text"
    >
      <ArrowLeft className="mr-2 h-4 w-4" />
      Go Back
    </Button>
  );
}

function DashboardLink() {
  return (
    <Link
      to="/"
      className="inline-flex justify-center items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors"
    >
      Go to Dashboard
    </Link>
  );
}

function LoginLink() {
  return (
    <Link
      to="/login"
      className="inline-flex justify-center items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors"
    >
      Go to Login
    </Link>
  );
}

function ContactHealerLink() {
  return (
    <a
      href="mailto:?subject=New%20Feedback%20Link%20Request&body=Hi%2C%20my%20feedback%20link%20has%20expired.%20Could%20you%20please%20send%20me%20a%20new%20one%3F"
      className="inline-flex justify-center items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors"
    >
      <Mail className="mr-2 h-4 w-4" />
      Contact Healer
    </a>
  );
}

const DEFAULT_ACTIONS: Record<ErrorStateVariant, ReactNode> = {
  forbidden: (
    <>
      <BackButton />
      <DashboardLink />
    </>
  ),
  expired: (
    <>
      <BackButton />
      <ContactHealerLink />
    </>
  ),
  notFound: (
    <>
      <BackButton />
      <DashboardLink />
    </>
  ),
  generic: <BackButton />,
  inviteExpired: (
    <>
      <BackButton />
      <LoginLink />
    </>
  ),
};

const CONTACT_HEALER_HREF =
  "mailto:?subject=New%20Feedback%20Link%20Request&body=Hi%2C%20my%20feedback%20link%20has%20expired.%20Could%20you%20please%20send%20me%20a%20new%20one%3F";

/** New-view default actions (sentence case, mockup buttons); the destinations match DEFAULT_ACTIONS. */
function newViewActions(variant: ErrorStateVariant): ReactNode {
  const back = (
    <button type="button" className="btn btn-secondary" onClick={() => window.history.back()}>
      Go back
    </button>
  );
  switch (variant) {
    case "forbidden":
    case "notFound":
      return <>{back}<Link to="/" className="btn btn-primary">Go to dashboard</Link></>;
    case "expired":
      return <>{back}<a href={CONTACT_HEALER_HREF} className="btn btn-primary"><Mail className="ico" aria-hidden="true" />Contact healer</a></>;
    case "inviteExpired":
      return <>{back}<Link to="/login" className="btn btn-primary">Go to login</Link></>;
    default:
      return back;
  }
}

interface ErrorStateProps {
  variant: ErrorStateVariant;
  /** Overrides the variant's base title. */
  title?: string;
  /** Overrides the variant's base message. Takes precedence over `error`. */
  message?: string;
  /** An API error (axios) — its RFC7807 `detail` is used as the message when `message` isn't provided. */
  error?: unknown;
  /** Overrides the variant's default action buttons. Pass `null` to render no actions. */
  actions?: ReactNode;
}

/**
 * Shared full-page status display for blocking states (forbidden, expired links,
 * not-found, generic errors). Message resolution order: explicit `message` prop >
 * `extractDetail(error)` (RFC7807 `detail` from an API error) > the variant's base message.
 */
export default function ErrorState({ variant, title, message, error, actions }: ErrorStateProps) {
  const { isNewView } = useUiVersion();
  const config = VARIANTS[variant];
  const Icon = config.icon;
  const resolvedMessage = message ?? extractDetail(error) ?? config.message;
  const resolvedActions = actions === undefined ? DEFAULT_ACTIONS[variant] : actions;

  if (isNewView) {
    const newActions = actions === undefined ? newViewActions(variant) : actions;
    return (
      <div className="hlx min-h-screen bg-background flex flex-col items-center justify-center p-4">
        <section className="surface" style={{ width: 420, maxWidth: "100%", padding: 34, borderRadius: 24, boxShadow: "var(--shadow-sheet)" }}>
          <div className="brand" style={{ padding: "0 0 22px" }}>
            <BrandMark size={32} />
            <span className="brand-name">HealingARK</span>
          </div>
          <div style={{ textAlign: "center" }}>
            <div className="avatar" style={{ width: 60, height: 60, margin: "0 auto 14px", background: "var(--warn-soft)", color: "var(--warn)" }}>
              <Icon style={{ width: 28, height: 28 }} aria-hidden="true" />
            </div>
            <span className="badge warn">{config.badge}</span>
            <h1 style={{ fontSize: 22, margin: "12px 0 8px" }}>{title ?? config.title}</h1>
            <p className="muted">{resolvedMessage}</p>
            {newActions && (
              <div style={{ marginTop: 22, display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap" }}>
                {newActions}
              </div>
            )}
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background flex flex-col items-center justify-center p-4">
      <div className="max-w-md w-full bg-card border border-border rounded-2xl shadow-lg p-8 text-center space-y-6">
        <div className="flex flex-col items-center justify-center space-y-4">
          <div className="bg-danger/10 p-4 rounded-full">
            <Icon className="h-12 w-12 text-danger" aria-hidden="true" />
          </div>
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-danger/10 text-danger">
            {config.badge}
          </span>
        </div>

        <div className="space-y-2">
          <h1 className="text-2xl font-bold text-text tracking-tight">{title ?? config.title}</h1>
          <p className="text-text-light text-sm">{resolvedMessage}</p>
        </div>

        {resolvedActions && (
          <div className="pt-4 flex flex-col space-y-3 sm:flex-row sm:space-y-0 sm:space-x-3 justify-center">
            {resolvedActions}
          </div>
        )}
      </div>
    </div>
  );
}
