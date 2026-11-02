import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { useGrantCustomPlan } from "./useAdminAccounts";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import api from "../../lib/axios";
import { toast } from "sonner";

const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, invalidate };
}

describe("useGrantCustomPlan", () => {
  beforeEach(() => vi.clearAllMocks());

  it("posts the plan to the healer's custom-plan endpoint and refreshes the accounts", async () => {
    mockedPost.mockResolvedValue({ data: { plan_tier: "PILOT" } });
    const { wrapper, invalidate } = setup();
    const { result } = renderHook(() => useGrantCustomPlan(), { wrapper });

    const payload = { plan_tag: "PILOT", credits: 100, validity_value: 2, validity_unit: "MONTHS" as const };
    await act(async () => {
      await result.current.mutateAsync({ healerId: "h-1", payload });
    });

    expect(mockedPost).toHaveBeenCalledWith("/api/admin/credit/accounts/HEALER/h-1/custom-plan", payload);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["admin-accounts"] });
    expect(toast.success).toHaveBeenCalledWith("PILOT plan set");
  });

  it("shows the server's reason when the grant is refused", async () => {
    mockedPost.mockRejectedValue({ response: { data: { detail: "Plan tag PRO is already a package name" } } });
    const { wrapper } = setup();
    const { result } = renderHook(() => useGrantCustomPlan(), { wrapper });

    await act(async () => {
      await result.current
        .mutateAsync({ healerId: "h-1", payload: { plan_tag: "PRO", credits: 5, validity_value: 0 } })
        .catch(() => undefined);
    });

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Plan tag PRO is already a package name"));
  });
});
