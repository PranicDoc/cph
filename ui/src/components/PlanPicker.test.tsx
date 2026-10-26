import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { mockPurchase, mockPackages } = vi.hoisted(() => ({
  mockPurchase: vi.fn(),
  mockPackages: vi.fn(),
}));

vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../hooks/usePurchasePackage", () => ({
  usePurchasePackage: () => ({ mutate: mockPurchase, isPending: false }),
}));
vi.mock("../hooks/useCreditAccount", () => ({
  useCreditAccount: () => ({ data: { plan_tier: "FREE", plan_expires_at: null } }),
}));
vi.mock("../hooks/useCreditPackages", async () => {
  const actual = await vi.importActual<typeof import("../hooks/useCreditPackages")>("../hooks/useCreditPackages");
  return { ...actual, useCreditPackages: () => mockPackages() };
});

import { PlanPicker } from "./PlanPicker";

const pkg = (over: Record<string, unknown>) => ({
  id: "id",
  name: "X",
  credits: 5,
  price_paise: 0,
  currency: "INR",
  active: true,
  display_order: 1,
  validity_value: 14,
  validity_unit: "DAYS",
  ...over,
});

describe("PlanPicker, free-priced packages", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPackages.mockReturnValue({
      isLoading: false,
      error: null,
      data: [
        pkg({ id: "free", name: "FREE", display_order: 0 }),
        pkg({ id: "trial", name: "BETA TRIAL", display_order: 1 }),
      ],
    });
  });

  it("purchases a free trial package instead of just closing", async () => {
    const onComplete = vi.fn();
    render(<PlanPicker ownerId="u1" ownerType="HEALER" onComplete={onComplete} />);

    await userEvent.setup().click(screen.getByRole("button", { name: "Start Free Trial" }));

    expect(mockPurchase).toHaveBeenCalledTimes(1);
    expect(mockPurchase.mock.calls[0][0]).toMatchObject({ packageId: "trial", ownerId: "u1" });
  });

  it("does not purchase the built-in FREE plan", async () => {
    mockPackages.mockReturnValue({
      isLoading: false,
      error: null,
      data: [pkg({ id: "free", name: "FREE", display_order: 0 })],
    });
    render(<PlanPicker ownerId="u1" ownerType="HEALER" onComplete={vi.fn()} />);

    // The account is on FREE, so its card is the current plan and has nothing to buy.
    expect(screen.getByRole("button", { name: "Current Plan" })).toBeDisabled();
    expect(mockPurchase).not.toHaveBeenCalled();
  });
});
