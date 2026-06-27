import { useState } from "react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from "recharts";
import { format, parseISO } from "date-fns";
import { AlertTriangle } from "lucide-react";
import { useCreditUsage } from "../hooks/useCreditUsage";
import { useDashboardSummary } from "../hooks/useDashboardSummary";
import { ChartPeriodPicker } from "./ChartPeriodPicker";
import { Skeleton } from "./ui/skeleton";
import type { ChartPeriod } from "../types/chart";

interface Props {
  healerId: string;
  initialPeriod?: ChartPeriod;
}

export function CreditUsageChart({ healerId, initialPeriod }: Props) {
  const [chartPeriod, setChartPeriod] = useState<ChartPeriod>(
    initialPeriod ?? { period: "30d" }
  );

  const { data, isLoading, isError, refetch } = useCreditUsage(
    healerId,
    chartPeriod
  );
  const { data: summaryData } = useDashboardSummary();

  const totalConsumed =
    data?.reduce((acc, d) => acc + (d.creditsConsumed ?? 0), 0) ?? 0;

  function renderChartArea() {
    if (isLoading) {
      return <Skeleton className="w-full h-[250px] rounded-xl" />;
    }

    if (isError) {
      return (
        <div className="bg-danger/10 border border-danger/20 rounded-xl px-4 py-4 flex items-center justify-between gap-4">
          <p className="text-sm text-danger">
            Unable to load data — try refreshing.
          </p>
          <button
            onClick={() => refetch()}
            className="bg-primary text-white rounded-lg px-4 py-2 text-sm font-semibold hover:bg-primary-dark transition-colors"
          >
            Retry
          </button>
        </div>
      );
    }

    if (!Array.isArray(data) || data.length === 0) {
      return (
        <div className="h-[250px] flex items-center justify-center">
          <p className="text-sm text-text-light">No data for this period</p>
        </div>
      );
    }

    return (
      <ResponsiveContainer width="100%" height={250}>
        <LineChart
          data={data}
          margin={{ top: 4, right: 8, bottom: 4, left: -20 }}
        >
          <CartesianGrid
            strokeDasharray="3 3"
            stroke="#E4E0ED"
            vertical={false}
          />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 12, fill: "#6B6880" }}
            tickLine={false}
            axisLine={false}
            tickFormatter={(d) => format(parseISO(d), "MMM d")}
          />
          <YAxis
            tick={{ fontSize: 12, fill: "#6B6880" }}
            tickLine={false}
            axisLine={false}
            allowDecimals={false}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: "#FFFFFF",
              border: "1px solid #E4E0ED",
              borderRadius: "12px",
              fontSize: "12px",
              color: "#2D2B35",
            }}
            cursor={{ stroke: "#E4E0ED" }}
          />
          <Line
            type="monotone"
            dataKey="creditsConsumed"
            stroke="#6C3FC5"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, fill: "#6C3FC5" }}
          />
        </LineChart>
      </ResponsiveContainer>
    );
  }

  return (
    <div className="bg-card rounded-2xl shadow-lg shadow-primary/20 p-6 flex flex-col gap-4">
      {/* Card header */}
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold text-text">Credit Usage</p>
        <ChartPeriodPicker value={chartPeriod} onChange={setChartPeriod} />
      </div>

      {/* Card body: 70/30 split */}
      <div className="flex flex-row gap-0">
        {/* Chart area: ~70% */}
        <div className="flex-1">{renderChartArea()}</div>

        {/* Info panel: ~30% */}
        <div className="w-[30%] flex flex-col gap-6 justify-center pl-6 border-l border-border">
          {/* Stat block 1: Credits Remaining */}
          <div>
            <p className="text-xs font-normal text-text-light uppercase tracking-wide mb-1">
              Credits Remaining
            </p>
            {summaryData?.creditServiceUnavailable ? (
              <div className="flex items-center gap-1">
                <span className="text-[28px] font-semibold text-text">—</span>
                <AlertTriangle className="w-4 h-4 text-warning" />
              </div>
            ) : (
              <p className="text-[28px] font-semibold text-text tabular-nums">
                {summaryData?.creditsRemaining?.toLocaleString() ?? "—"}
              </p>
            )}
          </div>

          {/* Stat block 2: Total Consumed */}
          <div>
            <p className="text-xs font-normal text-text-light uppercase tracking-wide mb-1">
              Total Consumed
            </p>
            <p className="text-[28px] font-semibold text-text tabular-nums">
              {totalConsumed.toLocaleString()}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
