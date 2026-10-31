import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { CheckoutCancelled, loadRazorpayCheckout, openRazorpayCheckout } from "../lib/razorpay";

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

interface OrderResponse {
  status: "CREATED" | "PAID";
  gateway: "noop" | "razorpay";
  order_id: string;
  amount_paise: number;
  currency: string;
  key_id: string;
  package_name: string;
  purchase: PurchaseResponse | null;
}

export function usePurchasePackage() {
  const qc = useQueryClient();
  return useMutation<PurchaseResponse, Error, PurchasePayload>({
    mutationFn: async (payload: PurchasePayload) => {
      const owner = { ownerType: payload.ownerType, ownerId: payload.ownerId };
      const { data: order } = await api.post<OrderResponse>(
        "/api/credits/purchase/orders",
        { packageId: payload.packageId, ...owner },
        { headers: { "X-Idempotency-Key": payload.idempotencyKey } }
      );
      if (order.status === "PAID") {
        if (!order.purchase) throw new Error("Order already paid — please refresh");
        return order.purchase;
      }

      let paymentId = "noop";
      let signature = "noop";
      if (order.gateway !== "noop") {
        await loadRazorpayCheckout();
        const paid = await openRazorpayCheckout({
          keyId: order.key_id,
          orderId: order.order_id,
          amountPaise: order.amount_paise,
          currency: order.currency,
          description: order.package_name,
          onPaymentFailed: (description) =>
            toast.error(description || "Payment failed — you can try again"),
        });
        if (paid.razorpay_order_id !== order.order_id) throw new Error("Payment order mismatch");
        paymentId = paid.razorpay_payment_id;
        signature = paid.razorpay_signature;
      }
      const { data } = await api.post<PurchaseResponse>(
        `/api/credits/purchase/orders/${order.order_id}/confirm`,
        { ...owner, payment_id: paymentId, signature }
      );
      return data;
    },
    onSuccess: () => {
      // Without this, PlanPicker keeps showing the pre-purchase plan_tier/balance
      // until something else happens to trigger a refetch (e.g. a remount).
      qc.invalidateQueries({ queryKey: ["credit-account"] });
      qc.invalidateQueries({ queryKey: ["my-credit-transactions"] });
      // An upgrade changes which plans may still be offered (only higher ones).
      qc.invalidateQueries({ queryKey: ["credit-packages"] });
      qc.invalidateQueries({ queryKey: ["dashboard-summary"] });
      toast.success("Purchase successful");
    },
    onError: (e) => {
      if (e instanceof CheckoutCancelled) {
        toast.info("Payment cancelled");
        return;
      }
      toast.error(extractDetail(e) || "Purchase failed — please try again", {
        duration: 5000,
      });
    },
  });
}
