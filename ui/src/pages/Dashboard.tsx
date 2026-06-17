import { Users, Calendar, CreditCard, FileText, RefreshCw } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useDashboardSummary } from "../hooks/useDashboardSummary";
import { KpiCard, KpiCardSkeleton } from "../components/KpiCard";
import { DateRangeFilter } from "../components/DateRangeFilter";

function getGreeting(firstName?: string): string {
  const hour = new Date().getHours();
  const prefix =
    hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  return firstName ? `${prefix}, ${firstName}` : "Dashboard";
}

export default function Dashboard() {
  const { user } = useAuth();
  const { data, isLoading, isError, refetch } = useDashboardSummary();

  const firstName = user?.fullName?.split(" ")[0];
  const heading = getGreeting(firstName);

  return (
    <div>
      <h1 className="text-xl font-semibold text-text mb-6">{heading}</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
        {isLoading ? (
          <>
            <KpiCardSkeleton />
            <KpiCardSkeleton />
            <KpiCardSkeleton />
            <KpiCardSkeleton />
          </>
        ) : isError ? (
          <div className="col-span-full bg-danger/10 border border-danger/20 rounded-2xl px-6 py-4 flex items-center justify-between gap-4">
            <p className="text-sm text-danger">
              Unable to load dashboard data — try refreshing.
            </p>
            <button
              onClick={() => refetch()}
              className="bg-primary text-white rounded-lg px-4 py-2 text-sm font-semibold flex items-center gap-2 hover:bg-primary-dark transition-colors flex-shrink-0"
            >
              <RefreshCw className="w-4 h-4" />
              Refresh Dashboard
            </button>
          </div>
        ) : (
          <>
            <KpiCard
              label="Active Patients"
              value={data?.activePatients ?? 0}
              icon={Users}
            />
            <KpiCard
              label="Sessions This Month"
              value={data?.sessionsThisMonth ?? 0}
              icon={Calendar}
            />
            <KpiCard
              label="Credits Remaining"
              value={data?.creditsRemaining ?? null}
              icon={CreditCard}
              unavailable={data?.creditServiceUnavailable === true}
            />
            <KpiCard
              label="PDFs This Month"
              value={data?.pdfsGeneratedThisMonth ?? 0}
              icon={FileText}
            />
          </>
        )}
      </div>

      <div className="mt-8">
        <DateRangeFilter />
      </div>
    </div>
  );
}
