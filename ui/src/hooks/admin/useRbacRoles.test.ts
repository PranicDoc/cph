import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { useRbacRoles, useCreateRole, useDeleteRole } from "./useRbacRoles";
import type { RbacRolesParams } from "./useRbacRoles";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import api from "../../lib/axios";
import { toast } from "sonner";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mockedDelete = api.delete as unknown as ReturnType<typeof vi.fn>;
const mockedToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

// Fresh QueryClient per test so cached role pages never leak between cases.
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    queryClient,
    Wrapper: ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children),
  };
}

const PAGE_STUB = {
  content: [
    { id: "r1", name: "Billing", description: null, resourceCount: 2, userCount: 3 },
  ],
  page: 2,
  size: 20,
  totalElements: 57,
  totalPages: 3,
  last: false,
};

describe("useRbacRoles data layer", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedDelete.mockReset();
    mockedToastError.mockReset();
  });

  it("issues GET /api/admin/roles with exactly the four query params", async () => {
    mockedGet.mockResolvedValue({ data: PAGE_STUB });
    const { Wrapper } = createWrapper();
    const params: RbacRolesParams = {
      search: "bill",
      page: 2,
      size: 20,
      sort: "name,desc",
    };
    const { result } = renderHook(() => useRbacRoles(params), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedGet).toHaveBeenCalledWith("/api/admin/roles", { params });
    const passedParams = mockedGet.mock.calls[0][1].params;
    expect(Object.keys(passedParams).sort()).toEqual([
      "page",
      "search",
      "size",
      "sort",
    ]);
  });

  it("exposes the backend `page` field, not the buggy `number` field", async () => {
    mockedGet.mockResolvedValue({ data: PAGE_STUB });
    const { Wrapper } = createWrapper();
    const { result } = renderHook(
      () => useRbacRoles({ search: "", page: 2, size: 20, sort: "name,asc" }),
      { wrapper: Wrapper }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.page).toBe(2);
    expect(result.current.data).not.toHaveProperty("number");
  });

  it("re-fetches when any of the four params change (distinct query keys)", async () => {
    mockedGet.mockResolvedValue({ data: PAGE_STUB });
    const { Wrapper } = createWrapper();
    const { result, rerender } = renderHook(
      (p: RbacRolesParams) => useRbacRoles(p),
      {
        wrapper: Wrapper,
        initialProps: {
          search: "a",
          page: 0,
          size: 20,
          sort: "name,asc",
        } as RbacRolesParams,
      }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledTimes(1);

    rerender({ search: "a", page: 1, size: 20, sort: "name,asc" });

    await waitFor(() => expect(mockedGet).toHaveBeenCalledTimes(2));
  });

  it("does NOT swallow a 409 self-lockout on create into a toast", async () => {
    const axiosError = {
      response: {
        status: 409,
        data: {
          detail:
            "This change would leave nobody able to manage roles. Assign the permission to another user first.",
        },
      },
    };
    mockedPost.mockRejectedValue(axiosError);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateRole(), { wrapper: Wrapper });

    act(() => {
      result.current.mutate({
        name: "X",
        description: null,
        grants: [{ resource: "ROLES", operations: ["READ"] }],
      });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(mockedToastError).not.toHaveBeenCalled();
    expect((result.current.error as any)?.response?.status).toBe(409);
  });

  it("invalidates the rbac-roles list after a successful delete", async () => {
    mockedDelete.mockResolvedValue({ data: undefined });
    const { Wrapper, queryClient } = createWrapper();
    const spy = vi.spyOn(queryClient, "invalidateQueries");

    const { result } = renderHook(() => useDeleteRole(), { wrapper: Wrapper });

    act(() => {
      result.current.mutate("r1");
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(spy).toHaveBeenCalledWith({ queryKey: ["rbac-roles"] });
  });
});
