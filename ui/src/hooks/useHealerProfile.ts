import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { useAuth } from "../context/AuthContext";

export interface HealerProfile {
  id: string;
  fullName: string;
  email: string;
  phone?: string;
  role: string;
  healingLevel?: string;
  active: boolean;
  clinicId: string;
  clinicCode: string;
  createdAt: string;
  hasPassword: boolean;
}

export function useHealerProfile() {
  return useQuery<HealerProfile>({
    queryKey: ["healer-profile"],
    queryFn: () => api.get("/api/v1/auth/profile").then((r) => r.data),
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  const { user, setUser } = useAuth();
  return useMutation({
    mutationFn: (body: { fullName: string; email: string; phone?: string }) =>
      api.put<HealerProfile>("/api/v1/auth/profile", body).then((r) => r.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["healer-profile"] });
      if (user) setUser({ ...user, fullName: data.fullName, email: data.email, phone: data.phone });
      toast.success("Profile updated");
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to update profile", { duration: 5000 }),
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (body: { currentPassword?: string; newPassword: string; confirmPassword: string }) =>
      api.post("/api/v1/auth/change-password", body).then((r) => r.data),
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to update password", { duration: 5000 }),
  });
}
