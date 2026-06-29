import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { UserCheck } from "lucide-react";
import { useState } from "react";
import { WorkQueueTable } from "./WorkQueueTable";
import { useFollowups } from "../hooks/useFollowups";
import api from "../lib/axios";
import type { FollowupPatient } from "../types/workqueue";

interface Props {
  healerId: string;
}

export function FollowupTable({ healerId }: Props) {
  const { data, isLoading, isError, refetch } = useFollowups(healerId);
  const [resending, setResending] = useState<string | null>(null);

  async function handleResend(caseId: string, isFinal: boolean) {
    setResending(caseId);
    try {
      await api.post(`/api/v1/cases/${caseId}/resend-feedback?isFinal=${isFinal}`);
      refetch();
    } finally {
      setResending(null);
    }
  }

  const columnHelper = createColumnHelper<FollowupPatient>();

  const columns = [
    columnHelper.accessor(
      (row) => `${row.patientFirstName} ${row.patientLastName}`,
      { id: "patient", header: "Patient" }
    ),
    columnHelper.accessor("sentAt", {
      header: "Link Sent",
      cell: (info) => format(parseISO(info.getValue()), "dd MMM yyyy HH:mm"),
    }),
    columnHelper.accessor("isFinal", {
      header: "Type",
      cell: (info) =>
        info.getValue() ? (
          <span className="bg-purple-100 text-purple-700 rounded-full px-2 py-0.5 text-xs font-medium">
            Final
          </span>
        ) : (
          <span className="bg-blue-100 text-blue-700 rounded-full px-2 py-0.5 text-xs font-medium">
            Interim
          </span>
        ),
    }),
    columnHelper.display({
      id: "action",
      header: "",
      cell: ({ row }) => {
        const isSending = resending === row.original.caseId;
        return (
          <button
            onClick={() => handleResend(row.original.caseId, row.original.isFinal)}
            disabled={isSending}
            className="text-xs font-medium text-primary hover:underline disabled:opacity-50"
          >
            {isSending ? "Sending…" : "Resend"}
          </button>
        );
      },
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
        emptyMessage="No pending feedback notifications"
      />
    </div>
  );
}
