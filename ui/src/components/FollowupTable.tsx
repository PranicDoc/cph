import { createColumnHelper } from "@tanstack/react-table";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
import { format, parseISO } from "date-fns";
import { UserCheck } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useState } from "react";
import { useUiVersion } from "../ui-version";
import { useFollowups } from "../hooks/useFollowups";
import api from "../lib/axios";
import type { FollowupPatient } from "../types/workqueue";

interface Props {
  healerId: string;
}

const PAGE_SIZE = 5;

export function FollowupTable({ healerId }: Props) {
  const { isNewView } = useUiVersion();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, refetch } = useFollowups(healerId);
  const resendMutation = useMutation({
    mutationKey: ["resend-feedback"],
    mutationFn: ({ caseId, isFinal }: { caseId: string; isFinal: boolean }) =>
      api.post(`/api/v1/cases/${caseId}/resend-feedback?isFinal=${isFinal}`),
    onSuccess: () => { toast.success("Feedback sent"); refetch(); },
    onError: (e) => toast.error(extractDetail(e) || "Failed to resend feedback", { duration: 5000 }),
  });

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
          <Badge className="bg-purple-100 text-purple-700">Final</Badge>
        ) : (
          <Badge className="bg-blue-100 text-blue-700">Interim</Badge>
        ),
    }),
    columnHelper.display({
      id: "action",
      header: "",
      cell: ({ row }) => {
        const isSending = resendMutation.isPending && resendMutation.variables?.caseId === row.original.caseId;
        return (
          <Button
            variant="link"
            onClick={() => resendMutation.mutate({ caseId: row.original.caseId, isFinal: row.original.isFinal })}
            disabled={resendMutation.isPending}
            className="h-auto p-0 text-xs font-medium"
          >
            {isSending ? "Sending…" : "Resend"}
          </Button>
        );
      },
    }),
  ];

  const all = data ?? [];
  const pages = Math.max(1, Math.ceil(all.length / PAGE_SIZE));
  const current = Math.min(page, pages - 1);
  const rows = isNewView ? all.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE) : all;

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <p className="text-sm font-semibold text-text mb-4">Follow-Up Required</p>
      <DataTable<FollowupPatient>
        columns={columns}
        data={rows}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={UserCheck}
        emptyMessage="No pending feedback notifications"
      />
      {isNewView && all.length > PAGE_SIZE && (
        <div className="flex items-center justify-between pt-4 text-sm">
          <span className="text-text-light">
            Showing {current * PAGE_SIZE + 1}-{Math.min(all.length, (current + 1) * PAGE_SIZE)} of {all.length}
          </span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={current === 0} onClick={() => setPage(current - 1)}>Previous</Button>
            <Button variant="outline" size="sm" disabled={current + 1 >= pages} onClick={() => setPage(current + 1)}>Next</Button>
          </div>
        </div>
      )}
    </div>
  );
}
