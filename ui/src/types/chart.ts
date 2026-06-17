export interface ChartPeriod {
  period: "7d" | "30d" | "90d" | "custom";
  from?: string; // YYYY-MM-DD, only when period === 'custom'
  to?: string; // YYYY-MM-DD, only when period === 'custom'
}

export function periodToApiParams(p: ChartPeriod): Record<string, string> {
  if (p.period === "custom" && p.from && p.to) {
    return { startDate: p.from, endDate: p.to };
  }
  return { period: p.period };
}
