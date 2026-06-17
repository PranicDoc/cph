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
import { Skeleton } from "./ui/skeleton";
import { ChartPeriodPicker } from "./ChartPeriodPicker";
import { usePatientGrowth } from "../hooks/usePatientGrowth";
import type { ChartPeriod } from "../types/chart";

interface Props {
  healerId: string;
  initialPeriod?: ChartPeriod;
}

export function PatientGrowthChart({ healerId, initialPeriod }: Props) {
  const [chartPeriod, setChartPeriod] = useState<ChartPeriod>(
    initialPeriod ?? { period: "30d" }
  );
  const { data, isLoading, isError, refetch } = usePatientGrowth(
    healerId,
    chartPeriod
  );

  return (
    <div className="bg-card rounded-2xl shadow-lg shadow-primary/20 p-6 flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold text-text">Patient Growth</p>
        <ChartPeriodPicker value={chartPeriod} onChange={setChartPeriod} />
      </div>

      <div className="h-[250px] w-full">
        {isLoading && (
          <Skeleton className="w-full h-[250px] rounded-xl" />
        )}

        {!isLoading && isError && (
          <div className="bg-danger/10 border border-danger/20 rounded-xl px-4 py-4 flex items-center justify-between gap-4">
            <p className="text-sm text-danger">
              Unable to load data — try refreshing.
            </p>
            <button
              className="bg-primary text-white rounded-lg px-4 py-2 text-sm font-semibold hover:bg-primary-dark transition-colors"
              onClick={() => refetch()}
            >
              Retry
            </button>
          </div>
        )}

        {!isLoading && !isError && data?.length === 0 && (
          <div className="h-[250px] flex items-center justify-center">
            <p className="text-sm text-text-light text-center">
              No data for this period
            </p>
          </div>
        )}

        {!isLoading && !isError && data && data.length > 0 && (
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
                dataKey="count"
                stroke="#6C3FC5"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4, fill: "#6C3FC5" }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
