import { useState } from "react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from "recharts";
import { Skeleton } from "./ui/skeleton";
import { Button } from "@/components/ui/button";
import { ChartPeriodPicker } from "./ChartPeriodPicker";
import { useTopSymptoms } from "../hooks/useTopSymptoms";
import type { ChartPeriod } from "../types/chart";

interface Props {
  healerId: string;
  initialPeriod?: ChartPeriod;
}

export function TopSymptomsChart({ healerId, initialPeriod }: Props) {
  const [chartPeriod, setChartPeriod] = useState<ChartPeriod>(
    initialPeriod ?? { period: "30d" }
  );
  const { data, isLoading, isError, refetch } = useTopSymptoms(
    healerId,
    chartPeriod
  );

  return (
    <div className="bg-card rounded-2xl shadow-lg shadow-primary/20 p-6 flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold text-text">Top Symptoms</p>
        <ChartPeriodPicker value={chartPeriod} onChange={setChartPeriod} />
      </div>

      <div className="h-[300px] w-full">
        {isLoading && (
          <Skeleton className="w-full h-[300px] rounded-xl" />
        )}

        {!isLoading && isError && (
          <div className="bg-danger/10 border border-danger/20 rounded-xl px-4 py-4 flex items-center justify-between gap-4">
            <p className="text-sm text-danger">
              Unable to load data — try refreshing.
            </p>
            <Button className="font-semibold" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        )}

        {!isLoading && !isError && data?.length === 0 && (
          <div className="h-[300px] flex items-center justify-center">
            <p className="text-sm text-text-light text-center">
              No data for this period
            </p>
          </div>
        )}

        {!isLoading && !isError && data && data.length > 0 && (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart
              data={data}
              margin={{ top: 4, right: 8, bottom: 4, left: -20 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="#E4E0ED"
                vertical={false}
              />
              <XAxis
                dataKey="symptom"
                tick={{ fontSize: 12, fill: "#6B6880" }}
                tickLine={false}
                axisLine={false}
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
                cursor={{ fill: "#F7F5FB" }}
              />
              <Bar dataKey="count" fill="#6C3FC5" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
