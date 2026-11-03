import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

export interface DeletedPatient {
  id: string;
  patientCode: string;
  firstName: string;
  lastName: string;
  healerId: string | null;
  healerName: string | null;
  deletedAt: string;
  /** When the nightly job erases the patient for good. */
  purgeAt: string;
  caseCount: number;
}

/** Super admin: the deleted patients still inside the retention window. */
export function useDeletedPatients(enabled = true) {
  return useQuery<DeletedPatient[]>({
    queryKey: ["deleted-patients"],
    queryFn: () => api.get("/api/v1/patients/trash").then((r) => r.data),
    enabled,
  });
}

export function useRestorePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/patients/${id}/restore`),
    onSuccess: () => {
      toast.success("Patient restored with their cases");
      queryClient.invalidateQueries({ queryKey: ["deleted-patients"] });
      queryClient.invalidateQueries({ queryKey: ["patients"] });
    },
    onError: (error) => toast.error(extractDetail(error) || "Could not restore the patient"),
  });
}
