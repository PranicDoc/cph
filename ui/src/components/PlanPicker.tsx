import { useRef, useState } from "react";
import { CreditCard, CheckCircle2, X } from "lucide-react";
import {
  useCreditPackages,
  isBuiltInFreePlan,
  type PackageResponse,
} from "../hooks/useCreditPackages";
import { usePurchasePackage } from "../hooks/usePurchasePackage";
import { useCreditAccount } from "../hooks/useCreditAccount";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { useUiVersion } from "../ui-version";
import { formatPlanEnd, formatValidity } from "../lib/validity";

interface PlanPickerProps {
  ownerId: string;
  ownerType: string;
  onComplete: () => void;
  onSkip?: () => void;
  /** New view in a dialog: draws the dialog header and a Close footer. */
  onClose?: () => void;
}

export function PlanPicker({
  ownerId,
  ownerType,
  onComplete,
  onSkip,
  onClose,
}: PlanPickerProps) {
  const { isNewView } = useUiVersion();
  const { data: packages, isLoading, error } = useCreditPackages();
  const { data: account } = useCreditAccount();
  const { mutate: purchase, isPending } = usePurchasePackage();
  const [selectedPkgId, setSelectedPkgId] = useState<string | null>(null);
  // CR-04: persist idempotency key in ref so retries reuse the same key; reset on success
  const idempotencyKeyRef = useRef<string | null>(null);

  const currentTier = account?.plan_tier?.toUpperCase();
  const isCurrentPlan = (pkg: PackageResponse) =>
    !!currentTier && pkg.name.toUpperCase() === currentTier;

  const handleSelectPlan = (pkg: PackageResponse) => {
    if (isCurrentPlan(pkg)) return;
    // Only the built-in FREE plan needs no purchase; a free trial package is claimed like any other.
    if (isBuiltInFreePlan(pkg)) {
      onComplete();
      return;
    }
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = crypto.randomUUID();
    }
    setSelectedPkgId(pkg.id);
    purchase(
      {
        packageId: pkg.id,
        ownerType,
        ownerId,
        idempotencyKey: idempotencyKeyRef.current,
      },
      {
        onSuccess: () => {
          idempotencyKeyRef.current = null;
          onComplete();
        },
        onError: () => {
          // key is kept in ref so a retry reuses the same idempotency key
          setSelectedPkgId(null);
        },
      }
    );
  };

  if (isNewView && onClose) {
    const sortedPkgs = [...(packages ?? [])].sort((a, b) => a.display_order - b.display_order);
    return (
      <>
        <header style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 12, padding: "22px 24px 16px", borderBottom: "1px solid var(--hair)" }}>
          <div>
            <h2>Choose a plan</h2>
            <p className="muted" style={{ marginTop: 4 }}>Select a credit plan to start new patient cases.</p>
          </div>
          <button type="button" className="icon-btn" aria-label="Close" onClick={onClose}>
            <X className="ico" />
          </button>
        </header>
        <div className="stack" style={{ flex: 1, minHeight: 0, overflow: "auto", padding: "20px 24px" }}>
          {isLoading ? (
            <div style={{ display: "flex", justifyContent: "center", padding: 32 }}><Spinner size="lg" /></div>
          ) : error ? (
            <p className="muted" style={{ textAlign: "center" }}>Failed to load plans. Please try again.</p>
          ) : (
            <>
              {account?.plan_expires_at && (
                <div className="small muted" style={{ textAlign: "center" }}>
                  Your {account.plan_tier} plan is active until{" "}
                  {new Date(account.plan_expires_at).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })}.
                </div>
              )}
              {sortedPkgs.length === 0 && (
                <p className="muted" style={{ textAlign: "center" }}>You are on our highest plan. Your plan stays active until it ends.</p>
              )}
              <div className="plans">
                {sortedPkgs.map((pkg) => {
                  const isFree = pkg.price_paise === 0;
                  const isCurrent = isCurrentPlan(pkg);
                  const busy = selectedPkgId === pkg.id && isPending;
                  return (
                    <div key={pkg.id} className="surface card" style={{ display: "flex", flexDirection: "column", gap: 10, boxShadow: isCurrent ? "0 0 0 2px var(--primary)" : undefined }}>
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
                        <div style={{ fontWeight: 700, fontSize: 16 }}>{pkg.name}</div>
                        {isCurrent && <span className="badge">Current</span>}
                      </div>
                      <div className="small muted">
                        {pkg.credits} credits{pkg.validity_value ? ` · valid ${formatValidity(pkg.validity_value, pkg.validity_unit)}` : ""}
                      </div>
                      <div className="num" style={{ fontSize: 20, fontWeight: 650 }}>
                        {isFree ? "Free" : `₹${(pkg.price_paise / 100).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`}
                      </div>
                      <div style={{ flex: 1 }} />
                      <button
                        type="button"
                        className={isCurrent ? "btn btn-secondary" : "btn btn-primary"}
                        style={{ width: "100%" }}
                        disabled={isPending || isCurrent}
                        onClick={() => handleSelectPlan(pkg)}
                      >
                        {busy ? "Processing…" : isCurrent ? "Current plan" : isFree ? (isBuiltInFreePlan(pkg) ? "Get started free" : "Start free trial") : "Select plan"}
                      </button>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: 10, padding: "14px 24px", borderTop: "1px solid var(--hair)" }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Close</button>
        </footer>
      </>
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 text-text-light">
        <p>Failed to load plans. Please try again.</p>
        {onSkip && (
          <Button
            variant="link"
            onClick={onSkip}
            className="mt-4 h-auto p-0 text-sm font-normal text-current underline"
          >
            Skip for now
          </Button>
        )}
      </div>
    );
  }

  const sorted = [...(packages ?? [])].sort(
    (a, b) => a.display_order - b.display_order
  );

  return (
    <div className="space-y-6">
      <div className={isNewView ? "space-y-1 text-left" : "text-center space-y-2"}>
        <div className={isNewView ? "flex items-center gap-2" : "flex items-center justify-center gap-2"}>
          {!isNewView && <CreditCard className="w-6 h-6 text-primary" />}
          <h2 className="text-xl sm:text-2xl font-bold text-primary-dark">
            Choose a Plan
          </h2>
        </div>
        <p className="text-text-light">
          Select a credit plan to start new patient cases.
        </p>
      </div>

      {account?.plan_expires_at && (
        <p className={isNewView ? "text-sm text-text-light" : "text-center text-sm text-text-light"}>
          Your {account.plan_tier} plan is active until {formatPlanEnd(account.plan_expires_at)}.
        </p>
      )}

      {sorted.length === 0 && (
        <p className="text-center text-text-light">
          You are on our highest plan. Your plan stays active until it ends.
        </p>
      )}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {sorted.map((pkg) => {
          const isFree = pkg.price_paise === 0;
          const isThisSelected = selectedPkgId === pkg.id && isPending;
          const isCurrent = isCurrentPlan(pkg);

          return (
            <div
              key={pkg.id}
              className={cn(
                "rounded-xl border p-5 space-y-3 shadow-sm bg-card flex flex-col",
                isCurrent ? "border-primary ring-1 ring-primary" : "border-border"
              )}
            >
              <div className="flex items-center justify-between gap-2">
                <p className="text-base sm:text-lg font-bold text-primary-dark leading-tight">{pkg.name}</p>
                {isCurrent && (
                  <span className="flex items-center gap-1 text-xs font-semibold text-primary shrink-0">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    Current
                  </span>
                )}
              </div>
              <p className="text-text-light text-sm">
                {pkg.credits} credits
                {pkg.validity_value ? ` · valid ${formatValidity(pkg.validity_value, pkg.validity_unit)}` : ""}
              </p>
              <p className="text-base sm:text-lg font-semibold text-primary">
                {isFree ? "Free" : `₹${(pkg.price_paise / 100).toFixed(2)}`}
              </p>
              <div className="flex-1" />
              <Button
                onClick={() => handleSelectPlan(pkg)}
                disabled={isPending || isCurrent}
                className={cn(
                  "w-full",
                  isFree && isBuiltInFreePlan(pkg) && !isCurrent && "bg-border text-text hover:bg-border/80"
                )}
              >
                {isThisSelected ? (
                  <span className="flex items-center justify-center gap-2">
                    <Spinner size="sm" className="text-current" />
                    Processing…
                  </span>
                ) : isCurrent ? (
                  "Current Plan"
                ) : isFree ? (
                  isBuiltInFreePlan(pkg) ? "Get Started Free" : "Start Free Trial"
                ) : (
                  "Select Plan"
                )}
              </Button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
