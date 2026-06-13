import { useMutation } from "@tanstack/react-query";
import api from "../lib/axios";

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
        .post("/api/credits/purchase", {
          package_id: payload.packageId,
          owner_type: payload.ownerType,
          owner_id: payload.ownerId,
          idempotency_key: payload.idempotencyKey,
        })
        .then((r) => r.data),
  });
}
