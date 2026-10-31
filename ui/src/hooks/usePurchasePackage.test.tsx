import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const { post, toast, load, open } = vi.hoisted(() => ({
  post: vi.fn(),
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
  load: vi.fn(),
  open: vi.fn(),
}));

vi.mock("../lib/axios", () => ({ default: { post } }));
vi.mock("sonner", () => ({ toast }));
vi.mock("../lib/razorpay", async () => {
  const actual = await vi.importActual<typeof import("../lib/razorpay")>("../lib/razorpay");
  return { ...actual, loadRazorpayCheckout: load, openRazorpayCheckout: open };
});

import { usePurchasePackage } from "./usePurchasePackage";
import { CheckoutCancelled } from "../lib/razorpay";

const payload = { packageId: "p1", ownerType: "HEALER", ownerId: "u1", idempotencyKey: "k1" };
const purchase = { purchase_reference: "ref", credits_added: 10, new_balance: 10 };
const order = (over: Record<string, unknown>) => ({
  data: { status: "CREATED", gateway: "razorpay", order_id: "order_1", amount_paise: 49900, currency: "INR", key_id: "rzp_k", package_name: "Pro", purchase: null, ...over },
});

function setup() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const spy = vi.spyOn(qc, "invalidateQueries");
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return { ...renderHook(() => usePurchasePackage(), { wrapper }), spy };
}

describe("usePurchasePackage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("razorpay: opens checkout and confirms with handler ids", async () => {
    post.mockResolvedValueOnce(order({})).mockResolvedValueOnce({ data: purchase });
    load.mockResolvedValue(undefined);
    open.mockResolvedValue({ razorpay_payment_id: "pay_1", razorpay_order_id: "order_1", razorpay_signature: "sig" });
    const { result, spy } = setup();
    result.current.mutate(payload);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(post.mock.calls[0][0]).toBe("/api/credits/purchase/orders");
    expect(post.mock.calls[0][1]).toEqual({ packageId: "p1", ownerType: "HEALER", ownerId: "u1" });
    expect(post.mock.calls[0][2]).toEqual({ headers: { "X-Idempotency-Key": "k1" } });
    expect(open).toHaveBeenCalledWith({ keyId: "rzp_k", orderId: "order_1", amountPaise: 49900, currency: "INR", description: "Pro" });
    expect(post.mock.calls[1][0]).toBe("/api/credits/purchase/orders/order_1/confirm");
    expect(post.mock.calls[1][1]).toEqual({ ownerType: "HEALER", ownerId: "u1", payment_id: "pay_1", signature: "sig" });
    expect(result.current.data).toEqual(purchase);
    expect(toast.success).toHaveBeenCalledWith("Purchase successful");
    expect(spy).toHaveBeenCalledWith({ queryKey: ["credit-account"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["credit-packages"] });
  });

  it("noop: no script load, confirms with noop", async () => {
    post.mockResolvedValueOnce(order({ gateway: "noop" })).mockResolvedValueOnce({ data: purchase });
    const { result } = setup();
    result.current.mutate(payload);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(load).not.toHaveBeenCalled();
    expect(open).not.toHaveBeenCalled();
    expect(post.mock.calls[1][1]).toMatchObject({ payment_id: "noop", signature: "noop" });
  });

  it("free: a single request", async () => {
    post.mockResolvedValueOnce(order({ status: "PAID", gateway: "noop", purchase }));
    const { result } = setup();
    result.current.mutate(payload);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledTimes(1);
    expect(result.current.data).toEqual(purchase);
  });

  it("dismiss: info toast, no confirm, no error toast", async () => {
    post.mockResolvedValueOnce(order({}));
    load.mockResolvedValue(undefined);
    open.mockRejectedValue(new CheckoutCancelled());
    const { result } = setup();
    result.current.mutate(payload);
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(toast.info).toHaveBeenCalledWith("Payment cancelled");
    expect(toast.error).not.toHaveBeenCalled();
    expect(post).toHaveBeenCalledTimes(1);
  });
});
