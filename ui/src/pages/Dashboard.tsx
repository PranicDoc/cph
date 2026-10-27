import { Users, Calendar, CreditCard, FileText, RefreshCw } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { versioned } from "../ui-version";
import { useAuth } from "../context/AuthContext";
import { useDashboardSummary } from "../hooks/useDashboardSummary";
import { KpiCard, KpiCardSkeleton } from "../components/KpiCard";
import { DateRangeFilter } from "../components/DateRangeFilter";
import type { ChartPeriod } from "../types/chart";
import { SessionTrendChart } from "../components/SessionTrendChart";
import { PatientGrowthChart } from "../components/PatientGrowthChart";
import { TopSymptomsChart } from "../components/TopSymptomsChart";
import { TopProtocolsChart } from "../components/TopProtocolsChart";
import { CreditUsageChart } from "../components/CreditUsageChart";
import { UpcomingSessionsTable } from "../components/UpcomingSessionsTable";
import { PendingReportsTable } from "../components/PendingReportsTable";
import { CaseListTable } from "../components/CaseListTable";
import { FollowupTable } from "../components/FollowupTable";
import { Button } from "@/components/ui/button";

function getGreeting(firstName?: string): string {
  const hour = new Date().getHours();
  const prefix =
    hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  return firstName ? `${prefix}, ${firstName}` : "Dashboard";
}

function ClassicDashboard() {
  const { user } = useAuth();
  const { data, isLoading, isError, refetch } = useDashboardSummary();

  const firstName = user?.fullName?.split(" ")[0] || undefined;
  const heading = getGreeting(firstName);

  const [searchParams] = useSearchParams();
  const globalPeriodKey = [
    searchParams.get("period") ?? "30d",
    searchParams.get("from") ?? "",
    searchParams.get("to") ?? "",
  ].join("-");

  const initialPeriod: ChartPeriod = {
    period: (searchParams.get("period") as ChartPeriod["period"]) ?? "30d",
    from: searchParams.get("from") ?? undefined,
    to: searchParams.get("to") ?? undefined,
  };

  const healerId = user?.id ?? "";

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
            <Button
              onClick={() => refetch()}
              className="font-semibold gap-2 flex-shrink-0"
            >
              <RefreshCw className="w-4 h-4" />
              Refresh Dashboard
            </Button>
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

      {/* Work Queue section */}
      <div className="mt-8">
        <p className="text-sm font-semibold text-text-light uppercase tracking-wide mb-4">
          Work Queue
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <UpcomingSessionsTable healerId={healerId} />
          <PendingReportsTable healerId={healerId} />
        </div>
        <div className="mt-6">
          <CaseListTable healerId={healerId} />
        </div>
        <div className="mt-6">
          <FollowupTable healerId={healerId} />
        </div>
      </div>

      {/* Trends section */}
      <div key={globalPeriodKey} className="mt-8">
        <p className="text-sm font-semibold text-text-light uppercase tracking-wide mb-4">
          Trends
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <SessionTrendChart healerId={healerId} initialPeriod={initialPeriod} />
          <PatientGrowthChart healerId={healerId} initialPeriod={initialPeriod} />
        </div>
      </div>

      {/* Insights section */}
      <div key={globalPeriodKey} className="mt-8">
        <p className="text-sm font-semibold text-text-light uppercase tracking-wide mb-4">
          Insights
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <TopSymptomsChart healerId={healerId} initialPeriod={initialPeriod} />
          <TopProtocolsChart healerId={healerId} initialPeriod={initialPeriod} />
        </div>
      </div>

      {/* Credit Usage section */}
      <div key={globalPeriodKey} className="mt-8">
        <p className="text-sm font-semibold text-text-light uppercase tracking-wide mb-4">
          Credit Usage
        </p>
        <CreditUsageChart healerId={healerId} initialPeriod={initialPeriod} />
      </div>
    </div>
  );
}

const Dashboard = versioned(ClassicDashboard, () => import("../ui-new/pages/NewDashboard"));
export default Dashboard;
