import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { CalendarCheck } from "lucide-react";
import { WorkQueueTable } from "./WorkQueueTable";
import { useUpcomingSessions } from "../hooks/useUpcomingSessions";
import type { UpcomingSession } from "../types/workqueue";

interface Props {
  healerId: string;
}

export function UpcomingSessionsTable({ healerId }: Props) {
  const { data, isLoading, isError, refetch } = useUpcomingSessions(healerId);

  const columnHelper = createColumnHelper<UpcomingSession>();

  const columns = [
    columnHelper.accessor((row) => `${row.patientFirstName} ${row.patientLastName}`, {
      id: "patient",
      header: "Patient",
    }),
    columnHelper.accessor("sessionDate", {
      header: "Date",
      cell: (info) => format(parseISO(info.getValue()), "dd MMM yyyy"),
    }),
    columnHelper.accessor("startTime", {
      header: "Time",
      cell: (info) => {
        const v = info.getValue();
        return v ? v.substring(0, 5) : "—";
      },
    }),
    columnHelper.accessor("status", {
      header: "Status",
      cell: (info) => {
        const s = info.getValue();
        const cls =
          s === "DRAFT"
            ? "bg-amber-100 text-amber-700 rounded-full px-2 py-0.5 text-xs font-medium"
            : "bg-green-100 text-green-700 rounded-full px-2 py-0.5 text-xs font-medium";
        return <span className={cls}>{s}</span>;
      },
    }),
  ];

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <p className="text-sm font-semibold text-text mb-4">Upcoming Sessions</p>
      <WorkQueueTable<UpcomingSession>
        columns={columns}
        data={data ?? []}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={CalendarCheck}
        emptyMessage="No upcoming sessions"
      />
    </div>
  );
}
