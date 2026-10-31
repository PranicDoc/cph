import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
type Mod = typeof import("./razorpay");
let mod: Mod;
let loadRazorpayCheckout: Mod["loadRazorpayCheckout"];
let openRazorpayCheckout: Mod["openRazorpayCheckout"];
let CheckoutCancelled: Mod["CheckoutCancelled"];

// The loader memoises its in-flight promise, so each test gets a fresh module.
async function freshModule() {
  vi.resetModules();
  mod = await import("./razorpay");
  ({ loadRazorpayCheckout, openRazorpayCheckout, CheckoutCancelled } = mod);
}

const SRC = "https://checkout.razorpay.com/v1/checkout.js";
const tags = () => document.querySelectorAll(`script[src="${SRC}"]`);

describe("loadRazorpayCheckout", () => {
  beforeEach(async () => {
    await freshModule();
    document.head.innerHTML = "";
    delete (window as { Razorpay?: unknown }).Razorpay;
  });

  it("appends the script only once across two calls", async () => {
    const p1 = loadRazorpayCheckout();
    const p2 = loadRazorpayCheckout();
    expect(tags()).toHaveLength(1);
    tags()[0].dispatchEvent(new Event("load"));
    await Promise.all([p1, p2]);
    await loadRazorpayCheckout();
    expect(tags()).toHaveLength(1);
  });

  it("rejects on error and removes the tag so a retry re-injects", async () => {
    const p = loadRazorpayCheckout();
    tags()[0].dispatchEvent(new Event("error"));
    await expect(p).rejects.toThrow();
    expect(tags()).toHaveLength(0);
    const retry = loadRazorpayCheckout();
    expect(tags()).toHaveLength(1);
    tags()[0].dispatchEvent(new Event("load"));
    await retry;
  });
});

describe("openRazorpayCheckout", () => {
  const opts = { keyId: "rzp_test_k", orderId: "order_1", amountPaise: 49900, currency: "INR", description: "Pro" };
  let captured: Record<string, unknown>;
  const on = vi.fn();
  const open = vi.fn();

  beforeEach(async () => {
    await freshModule();
    on.mockReset();
    open.mockReset();
    (window as unknown as { Razorpay: unknown }).Razorpay = function (o: Record<string, unknown>) {
      captured = o;
      return { open, on };
    };
  });
  afterEach(() => {
    delete (window as { Razorpay?: unknown }).Razorpay;
  });

  it("resolves with the handler payload", async () => {
    const p = openRazorpayCheckout(opts);
    expect(captured).toMatchObject({ key: "rzp_test_k", order_id: "order_1", amount: 49900, currency: "INR" });
    expect(open).toHaveBeenCalled();
    const res = { razorpay_payment_id: "pay_1", razorpay_order_id: "order_1", razorpay_signature: "sig" };
    (captured.handler as (r: unknown) => void)(res);
    await expect(p).resolves.toEqual(res);
  });

  it("rejects with CheckoutCancelled on dismiss", async () => {
    const p = openRazorpayCheckout(opts);
    (captured.modal as { ondismiss: () => void }).ondismiss();
    await expect(p).rejects.toBeInstanceOf(CheckoutCancelled);
  });

  it("rejects with the description on payment.failed", async () => {
    const p = openRazorpayCheckout(opts);
    expect(on).toHaveBeenCalledWith("payment.failed", expect.any(Function));
    on.mock.calls[0][1]({ error: { description: "Card declined" } });
    await expect(p).rejects.toThrow("Card declined");
  });
});
