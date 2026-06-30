import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";

export interface PurchasePayload {
  packageId: string;
  ownerType: string;
  ownerId: string;
  idempotencyKey: string;
}

export interface PurchaseResponse {
  purchase_reference: string;
  credits_added: number;
  new_balance: number;
}

export function usePurchasePackage() {
  return useMutation<PurchaseResponse, Error, PurchasePayload>({
    mutationFn: (payload: PurchasePayload) =>
      api
        .post(
          "/api/credits/purchase",
          {
            packageId: payload.packageId,
            ownerType: payload.ownerType,
            ownerId: payload.ownerId,
          },
          {
            headers: {
              "X-Idempotency-Key": payload.idempotencyKey,
            },
          }
        )
        .then((r) => r.data),
    onSuccess: () => toast.success("Purchase successful"),
    onError: (e) =>
      toast.error(extractDetail(e) || "Purchase failed — please try again", {
        duration: 5000,
      }),
  });
}
