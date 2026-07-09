import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { CalendarCheck } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { useUpcomingSessions } from "../hooks/useUpcomingSessions";
import type { UpcomingSession } from "../types/workqueue";

interface Props {
  healerId: string;
}

export function UpcomingSessionsTable({ healerId }: Props) {
  const { data, isLoading, isError, refetch } = useUpcomingSessions(healerId);

  const columnHelper = createColumnHelper<UpcomingSession>();

  const columns = [
    columnHelper.accessor(
      (row) => `${row.patientFirstName} ${row.patientLastName}`,
      { id: "patient", header: "Patient" }
    ),
    columnHelper.accessor("date", {
      header: "Date",
      cell: (info) => {
        const v = info.getValue();
        return v ? format(parseISO(v), "dd MMM yyyy") : "—";
      },
    }),
    columnHelper.accessor("startTime", {
      header: "Time",
      cell: (info) => {
        const v = info.getValue();
        return v ? v.substring(0, 5) : "—";
      },
    }),
    columnHelper.accessor("type", {
      header: "Status",
      cell: (info) => {
        const t = info.getValue();
        if (t === "DRAFT_SESSION") {
          return (
            <Badge className="bg-amber-100 text-amber-700">Draft Session</Badge>
          );
        }
        return <Badge className="bg-blue-100 text-blue-700">Active Case</Badge>;
      },
    }),
  ];

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <p className="text-sm font-semibold text-text mb-4">Active Work</p>
      <DataTable<UpcomingSession>
        columns={columns}
        data={data ?? []}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={CalendarCheck}
        emptyMessage="No draft sessions or active cases"
      />
    </div>
  );
}
