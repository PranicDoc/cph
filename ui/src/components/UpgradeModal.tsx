import { useRef, useState } from "react";
import { CreditCard } from "lucide-react";
import { useCreditPackages, isBuiltInFreePlan, type PackageResponse } from "../hooks/useCreditPackages";
import { usePurchasePackage } from "../hooks/usePurchasePackage";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

interface UpgradeModalProps {
  isOpen: boolean;
  ownerId: string;
  ownerType: string;
  onClose: () => void;
  /** Repeats what ran out of credits, once a plan is bought. */
  onRetry: () => void;
}

export function UpgradeModal({
  isOpen,
  ownerId,
  ownerType,
  onClose,
  onRetry,
}: UpgradeModalProps) {
  const { data: packages, isLoading, error } = useCreditPackages();
  const { mutate: purchase, isPending } = usePurchasePackage();
  const [selectedPkgId, setSelectedPkgId] = useState<string | null>(null);
  // CR-04: persist idempotency key in ref so retries reuse the same key; reset on success
  const idempotencyKeyRef = useRef<{ pkgId: string; key: string } | null>(null);

  const handleUpgrade = (pkg: PackageResponse) => {
    if (isBuiltInFreePlan(pkg)) return;
    // A key belongs to one package: choosing a different package gets a fresh key.
    if (idempotencyKeyRef.current?.pkgId !== pkg.id) {
      idempotencyKeyRef.current = { pkgId: pkg.id, key: crypto.randomUUID() };
    }
    setSelectedPkgId(pkg.id);
    purchase(
      {
        packageId: pkg.id,
        ownerType,
        ownerId,
        idempotencyKey: idempotencyKeyRef.current.key,
      },
      {
        onSuccess: () => {
          idempotencyKeyRef.current = null;
          onClose();
          onRetry();
        },
        onError: () => {
          // key is kept in ref so a retry reuses the same idempotency key
          setSelectedPkgId(null);
        },
      }
    );
  };

  const sorted = [...(packages ?? [])].sort(
    (a, b) => a.display_order - b.display_order
  );

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-full sm:max-w-2xl gap-0 rounded-t-2xl sm:rounded-2xl max-h-[92vh] flex flex-col p-0 max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        {/* Drag handle pill (mobile only) */}
        <div className="sm:hidden flex justify-center pt-3 pb-1 shrink-0">
          <div className="w-10 h-1 bg-border rounded-full" />
        </div>

        {/* Scrollable body */}
        <div className="flex-1 min-h-0 overflow-y-auto p-5 sm:p-8">
          <div className="space-y-1.5 mb-5">
            <div className="flex items-center gap-2">
              <CreditCard className="w-5 h-5 sm:w-6 sm:h-6 text-primary shrink-0" />
              <DialogTitle className="text-xl sm:text-2xl font-bold text-primary-dark">
                Upgrade Your Plan
              </DialogTitle>
            </div>
            <p className="text-sm text-text-light">
              You've run out of credits. Choose a plan to keep starting new cases.
            </p>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-10">
              <Spinner size="lg" />
            </div>
          ) : error ? (
            <div className="text-center py-10 text-text-light">
              <p>Failed to load plans. Please try again.</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              {sorted.map((pkg) => {
                const isFree = pkg.price_paise === 0;
                const isThisSelected = selectedPkgId === pkg.id && isPending;

                return (
                  <div
                    key={pkg.id}
                    className="rounded-xl border border-border p-4 sm:p-5 space-y-2 shadow-sm flex flex-col"
                  >
                    <p className="text-base sm:text-lg font-bold text-primary-dark leading-tight">{pkg.name}</p>
                    <p className="text-text-light text-sm">{pkg.credits} credits</p>
                    <p className="text-base sm:text-lg font-semibold text-primary">
                      {isFree ? "Free" : `₹${(pkg.price_paise / 100).toFixed(2)}`}
                    </p>
                    <div className="flex-1" />
                    {isBuiltInFreePlan(pkg) ? (
                      <span className="w-full text-center px-4 py-2 rounded-lg text-sm font-medium bg-border text-text pointer-events-none">
                        Current Plan
                      </span>
                    ) : (
                      <Button
                        onClick={() => handleUpgrade(pkg)}
                        disabled={isPending}
                        className="w-full"
                      >
                        {isThisSelected ? (
                          <span className="flex items-center justify-center gap-2">
                            <Spinner size="sm" className="text-current" />
                            Processing…
                          </span>
                        ) : isFree ? (
                          "Start free trial"
                        ) : (
                          "Upgrade"
                        )}
                      </Button>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
