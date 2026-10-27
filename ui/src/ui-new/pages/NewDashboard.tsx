import { useNavigate, useSearchParams } from "react-router-dom";
import { AlertTriangle, Plus, RefreshCw } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import { useDashboardSummary } from "../../hooks/useDashboardSummary";
import { DateRangeFilter } from "../../components/DateRangeFilter";
import type { ChartPeriod } from "../../types/chart";
import { SessionTrendChart } from "../../components/SessionTrendChart";
import { PatientGrowthChart } from "../../components/PatientGrowthChart";
import { TopProtocolsList, TopSymptomsList } from "../dashboard/RankedCard";
import { CreditUsageChart } from "../../components/CreditUsageChart";
import { PendingReportsTable } from "../../components/PendingReportsTable";
import { CaseListTable } from "../../components/CaseListTable";
import { FollowupTable } from "../../components/FollowupTable";

function greeting(firstName?: string): string {
  const hour = new Date().getHours();
  const prefix = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  return firstName ? `${prefix}, ${firstName}` : "Dashboard";
}

function Kpi({ label, value, unavailable }: { label: string; value: number | null | undefined; unavailable?: boolean }) {
  return (
    <div className="surface kpi">
      <div className="k-l">{label}</div>
      <div className="k-v">{unavailable ? "—" : (value?.toLocaleString() ?? "—")}</div>
      {unavailable && (
        <div className="k-d warn-text" style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <AlertTriangle className="ico" style={{ width: 14, height: 14 }} /> Unavailable
        </div>
      )}
    </div>
  );
}

/** New-view dashboard: the same figures, queues and charts as the classic page, with the mockup's header and KPI cards. */
export default function NewDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch } = useDashboardSummary();
  const firstName = user?.fullName?.split(" ")[0] || undefined;
  const [searchParams] = useSearchParams();
  const periodKey = [searchParams.get("period") ?? "30d", searchParams.get("from") ?? "", searchParams.get("to") ?? ""].join("-");
  const initialPeriod: ChartPeriod = {
    period: (searchParams.get("period") as ChartPeriod["period"]) ?? "30d",
    from: searchParams.get("from") ?? undefined,
    to: searchParams.get("to") ?? undefined,
  };
  const healerId = user?.id ?? "";

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>{greeting(firstName)}</h1>
          <p className="muted" style={{ marginTop: 6 }}>Here is your practice at a glance.</p>
        </div>
        <button className="btn btn-primary" type="button" onClick={() => navigate("/registration")}>
          <Plus className="ico" /> New patient
        </button>
      </div>

      {isLoading ? (
        <div className="kpis">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="surface kpi" aria-hidden="true">
              <div className="skel" style={{ height: 12, width: 110 }} />
              <div className="skel" style={{ height: 30, width: 80, marginTop: 12 }} />
            </div>
          ))}
        </div>
      ) : isError ? (
        <div className="surface card" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
          <p className="err" style={{ margin: 0 }}>Unable to load dashboard data — try refreshing.</p>
          <button className="btn btn-primary" type="button" onClick={() => refetch()}>
            <RefreshCw className="ico" /> Refresh dashboard
          </button>
        </div>
      ) : (
        <div className="kpis">
          <Kpi label="Active patients" value={data?.activePatients ?? 0} />
          <Kpi label="Sessions this month" value={data?.sessionsThisMonth ?? 0} />
          <Kpi label="Credits remaining" value={data?.creditsRemaining ?? null} unavailable={data?.creditServiceUnavailable === true} />
          <Kpi label="PDFs this month" value={data?.pdfsGeneratedThisMonth ?? 0} />
        </div>
      )}

      <DateRangeFilter />

      <section>
        <h3 className="ongr" style={{ margin: "4px 4px 12px" }}>Work queue</h3>
        <div className="hl-2col">
          <CaseListTable healerId={healerId} />
          <PendingReportsTable healerId={healerId} />
        </div>
        <div style={{ marginTop: 16 }}>
          <FollowupTable healerId={healerId} />
        </div>
      </section>

      <section key={`trends-${periodKey}`}>
        <h3 className="ongr" style={{ margin: "4px 4px 12px" }}>Trends</h3>
        <div className="hl-2col">
          <SessionTrendChart healerId={healerId} initialPeriod={initialPeriod} />
          <PatientGrowthChart healerId={healerId} initialPeriod={initialPeriod} />
        </div>
      </section>

      <section key={`insights-${periodKey}`}>
        <h3 className="ongr" style={{ margin: "4px 4px 12px" }}>Insights</h3>
        <div className="hl-2col">
          <TopSymptomsList healerId={healerId} initialPeriod={initialPeriod} />
          <TopProtocolsList healerId={healerId} initialPeriod={initialPeriod} />
        </div>
      </section>

      <section key={`credits-${periodKey}`}>
        <h3 className="ongr" style={{ margin: "4px 4px 12px" }}>Credit usage</h3>
        <CreditUsageChart healerId={healerId} initialPeriod={initialPeriod} />
      </section>
    </div>
  );
}
