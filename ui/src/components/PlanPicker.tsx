import { useEffect, useRef, useState } from "react";
import { Loader2, CreditCard } from "lucide-react";
import {
  useCreditPackages,
  type PackageResponse,
} from "../hooks/useCreditPackages";
import { usePurchasePackage } from "../hooks/usePurchasePackage";

interface PlanPickerProps {
  ownerId: string;
  ownerType: string;
  onComplete: () => void;
  onSkip?: () => void;
}

export function PlanPicker({
  ownerId,
  ownerType,
  onComplete,
  onSkip,
}: PlanPickerProps) {
  const { data: packages, isLoading, error } = useCreditPackages();
  const { mutate: purchase, isPending } = usePurchasePackage();
  const [purchaseError, setPurchaseError] = useState<string | null>(null);
  const [selectedPkgId, setSelectedPkgId] = useState<string | null>(null);
  // CR-04: persist idempotency key in ref so retries reuse the same key; reset on success
  const idempotencyKeyRef = useRef<string | null>(null);

  const handleSelectPlan = (pkg: PackageResponse) => {
    if (pkg.price_paise === 0) {
      onComplete();
      return;
    }
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = crypto.randomUUID();
    }
    setPurchaseError(null);
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
          setPurchaseError("Purchase failed. Please try again.");
        },
      }
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 text-text-light">
        <p>Failed to load plans. Please try again.</p>
        {onSkip && (
          <button onClick={onSkip} className="mt-4 text-sm underline">
            Skip for now
          </button>
        )}
      </div>
    );
  }

  const sorted = [...(packages ?? [])].sort(
    (a, b) => a.display_order - b.display_order
  );

  return (
    <div className="space-y-6">
      <div className="text-center space-y-2">
        <div className="flex items-center justify-center gap-2">
          <CreditCard className="w-6 h-6 text-primary" />
          <h2 className="text-2xl font-bold text-primary-dark">
            Choose a Plan
          </h2>
        </div>
        <p className="text-text-light">
          Select a credit plan to start generating patient PDFs.
        </p>
      </div>

      {purchaseError && (
        <p className="text-center text-sm text-red-500">{purchaseError}</p>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {sorted.map((pkg) => {
          const isFree = pkg.price_paise === 0;
          const isThisSelected = selectedPkgId === pkg.id && isPending;

          return (
            <div
              key={pkg.id}
              className="rounded-xl border border-border p-6 space-y-3 shadow-sm bg-card flex flex-col"
            >
              <p className="text-xl font-bold text-primary-dark">{pkg.name}</p>
              <p className="text-text-light text-sm">{pkg.credits} credits</p>
              <p className="text-lg font-semibold text-primary">
                {isFree ? "Free" : `₹${(pkg.price_paise / 100).toFixed(2)}`}
              </p>
              <div className="flex-1" />
              <button
                onClick={() => handleSelectPlan(pkg)}
                disabled={isPending}
                className={`w-full px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 ${
                  isFree
                    ? "bg-border text-text hover:bg-border/80"
                    : "bg-primary text-white hover:bg-primary-dark"
                }`}
              >
                {isThisSelected ? (
                  <span className="flex items-center justify-center gap-2">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Processing…
                  </span>
                ) : isFree ? (
                  "Get Started Free"
                ) : (
                  "Select Plan"
                )}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
