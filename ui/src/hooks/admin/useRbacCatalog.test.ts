import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { useRbacCatalog } from "./useRbacCatalog";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn() },
}));

import api from "../../lib/axios";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function wrapperFor(queryClient: QueryClient) {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

const CATALOG_STUB = [
  {
    resource: "ROLES",
    label: "Roles",
    verbs: [
      { value: "READ", label: "View", description: "View records", locked: false },
      {
        value: "UPDATE",
        label: "Update",
        description: "Edit existing records",
        locked: true,
      },
    ],
  },
];

describe("useRbacCatalog", () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it("reads GET /api/admin/roles/catalog", async () => {
    mockedGet.mockResolvedValue({ data: CATALOG_STUB });
    const { result } = renderHook(() => useRbacCatalog(), {
      wrapper: wrapperFor(makeClient()),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedGet).toHaveBeenCalledWith("/api/admin/roles/catalog");
    expect(mockedGet).toHaveBeenCalledTimes(1);
  });

  it("preserves the {resource,label,verbs:[{value,label,description,locked}]} wire shape", async () => {
    mockedGet.mockResolvedValue({ data: CATALOG_STUB });
    const { result } = renderHook(() => useRbacCatalog(), {
      wrapper: wrapperFor(makeClient()),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(CATALOG_STUB);
    expect(result.current.data?.[0].verbs[0]).toEqual({
      value: "READ",
      label: "View",
      description: "View records",
      locked: false,
    });
    expect(result.current.data?.[0].verbs[1].locked).toBe(true);
  });

  it("does not refetch within the stale window on a second mount", async () => {
    mockedGet.mockResolvedValue({ data: CATALOG_STUB });
    const client = makeClient();
    const wrapper = wrapperFor(client);

    const first = renderHook(() => useRbacCatalog(), { wrapper });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledTimes(1);

    const second = renderHook(() => useRbacCatalog(), { wrapper });
    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));

    expect(mockedGet).toHaveBeenCalledTimes(1);
  });
});
