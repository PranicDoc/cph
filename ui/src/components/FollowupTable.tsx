import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { UserCheck } from "lucide-react";
import { WorkQueueTable } from "./WorkQueueTable";
import { useFollowups } from "../hooks/useFollowups";
import type { FollowupPatient } from "../types/workqueue";

interface Props {
  healerId: string;
}

export function FollowupTable({ healerId }: Props) {
  const { data, isLoading, isError, refetch } = useFollowups(healerId);

  const columnHelper = createColumnHelper<FollowupPatient>();

  const columns = [
    columnHelper.accessor((row) => `${row.patientFirstName} ${row.patientLastName}`, {
      id: "patient",
      header: "Patient",
    }),
    columnHelper.accessor("lastSessionDate", {
      header: "Last Session",
      cell: (info) => format(parseISO(info.getValue()), "dd MMM yyyy"),
    }),
    columnHelper.accessor("daysSinceLastSession", {
      header: "Days Since",
      sortingFn: "basic",
      cell: (info) => `${info.getValue()} days`,
    }),
  ];

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <p className="text-sm font-semibold text-text mb-4">Follow-Up Required</p>
      <WorkQueueTable<FollowupPatient>
        columns={columns}
        data={data ?? []}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={UserCheck}
        emptyMessage="No patients overdue for follow-up"
      />
    </div>
  );
}
