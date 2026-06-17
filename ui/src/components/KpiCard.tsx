import { AlertTriangle, type LucideIcon } from "lucide-react";
import { Skeleton } from "./ui/skeleton";

interface KpiCardProps {
  label: string;
  value: number | null;
  icon: LucideIcon;
  unavailable?: boolean;
}

export function KpiCard({
  label,
  value,
  icon: Icon,
  unavailable = false,
}: KpiCardProps) {
  return (
    <div className="bg-card rounded-2xl shadow-lg shadow-primary/20 p-6 flex items-start justify-between">
      <div>
        <p className="text-xs font-normal text-text-light uppercase tracking-wide">
          {label}
        </p>
        <p className="text-[28px] font-semibold text-text mt-2 tabular-nums">
          {unavailable ? "—" : (value?.toLocaleString() ?? "—")}
        </p>
        {unavailable && (
          <div className="flex items-center gap-1 mt-1">
            <AlertTriangle className="w-3 h-3 text-warning" />
            <span className="text-xs text-text-light">Unavailable</span>
          </div>
        )}
      </div>
      <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center flex-shrink-0">
        <Icon className="w-6 h-6 text-primary" />
      </div>
    </div>
  );
}

export function KpiCardSkeleton() {
  return (
    <div className="bg-card rounded-2xl shadow-lg shadow-primary/20 p-6 flex items-start justify-between">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-3 w-28 rounded" />
        <Skeleton className="h-8 w-20 rounded" />
      </div>
      <Skeleton className="w-12 h-12 rounded-xl flex-shrink-0" />
    </div>
  );
}
