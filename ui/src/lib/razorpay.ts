const CHECKOUT_SRC = "https://checkout.razorpay.com/v1/checkout.js";

export interface RazorpaySuccess {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

export class CheckoutCancelled extends Error {
  constructor(message = "Payment cancelled") {
    super(message);
    this.name = "CheckoutCancelled";
  }
}

interface RazorpayOptions {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  handler: (response: RazorpaySuccess) => void;
  modal: { ondismiss: () => void };
}

interface RazorpayFailure {
  error?: { description?: string };
}

interface RazorpayInstance {
  open: () => void;
  on: (event: "payment.failed", cb: (response: RazorpayFailure) => void) => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

let loading: Promise<void> | null = null;

/** Injects Razorpay's checkout.js exactly once; a failed load can be retried. */
export function loadRazorpayCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  if (loading) return loading;
  loading = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = CHECKOUT_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      script.remove();
      loading = null;
      reject(new Error("Could not load the payment gateway. Check your connection and try again."));
    };
    document.head.appendChild(script);
  });
  return loading;
}

export function openRazorpayCheckout(o: {
  keyId: string;
  orderId: string;
  amountPaise: number;
  currency: string;
  description: string;
}): Promise<RazorpaySuccess> {
  return new Promise<RazorpaySuccess>((resolve, reject) => {
    const Ctor = window.Razorpay;
    if (!Ctor) {
      reject(new Error("Payment gateway is not loaded"));
      return;
    }
    const rzp = new Ctor({
      key: o.keyId,
      order_id: o.orderId,
      amount: o.amountPaise,
      currency: o.currency,
      name: "PranicDoc",
      description: o.description,
      handler: (response) => resolve(response),
      modal: { ondismiss: () => reject(new CheckoutCancelled()) },
    });
    rzp.on("payment.failed", (r) => reject(new Error(r.error?.description || "Payment failed")));
    rzp.open();
  });
}
