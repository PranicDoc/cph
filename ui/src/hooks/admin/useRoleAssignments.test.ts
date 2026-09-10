import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import {
  useRoleAssignments,
  useAssignUser,
  useUnassignUser,
} from "./useRoleAssignments";
import { extractDetail } from "../../lib/utils";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
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

const LOCKOUT_DETAIL =
  "This change would leave nobody able to manage roles. Assign the Roles Update permission to another active user first.";

describe("useRoleAssignments data layer", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedDelete.mockReset();
    mockedToastError.mockReset();
  });

  it("reads GET /api/admin/roles/{roleId}/users", async () => {
    mockedGet.mockResolvedValue({ data: [] });
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useRoleAssignments("r1"), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith("/api/admin/roles/r1/users");
  });

  it("is disabled (no request) when roleId is undefined", async () => {
    mockedGet.mockResolvedValue({ data: [] });
    const { Wrapper } = createWrapper();
    renderHook(() => useRoleAssignments(undefined), { wrapper: Wrapper });

    await act(async () => {
      await Promise.resolve();
    });
    expect(mockedGet).not.toHaveBeenCalled();
  });

  it("assigns with a body whose only key is userId", async () => {
    mockedPost.mockResolvedValue({ data: undefined });
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useAssignUser("r1"), { wrapper: Wrapper });

    act(() => {
      result.current.mutate("u1");
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedPost).toHaveBeenCalledWith("/api/admin/roles/r1/users", {
      userId: "u1",
    });
    expect(Object.keys(mockedPost.mock.calls[0][1])).toEqual(["userId"]);
  });

  it("unassigns by path", async () => {
    mockedDelete.mockResolvedValue({ data: undefined });
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useUnassignUser("r1"), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.mutate("u1");
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedDelete).toHaveBeenCalledWith("/api/admin/roles/r1/users/u1");
  });

  it("invalidates both the assignment list and the roles list on assign success", async () => {
    mockedPost.mockResolvedValue({ data: undefined });
    const { Wrapper, queryClient } = createWrapper();
    const spy = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useAssignUser("r1"), { wrapper: Wrapper });

    act(() => {
      result.current.mutate("u1");
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(spy).toHaveBeenCalledWith({ queryKey: ["role-assignments", "r1"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["rbac-roles"] });
  });

  it("invalidates both caches on unassign success", async () => {
    mockedDelete.mockResolvedValue({ data: undefined });
    const { Wrapper, queryClient } = createWrapper();
    const spy = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useUnassignUser("r1"), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.mutate("u1");
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(spy).toHaveBeenCalledWith({ queryKey: ["role-assignments", "r1"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["rbac-roles"] });
  });

  it("does NOT swallow a 409 self-lockout on unassign into a toast", async () => {
    const axiosError = {
      response: { status: 409, data: { detail: LOCKOUT_DETAIL } },
    };
    mockedDelete.mockRejectedValue(axiosError);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useUnassignUser("r1"), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.mutate("u1");
    });
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(mockedToastError).not.toHaveBeenCalled();
    expect((result.current.error as any)?.response?.status).toBe(409);
    expect(extractDetail(result.current.error)).toBe(LOCKOUT_DETAIL);
  });
});
