import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn() },
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import api from "../../lib/axios";
import { useProtocolsPage } from "./useMasterData";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

const page = { content: [{ id: "p1", name: "Fever" }], page: 1, size: 50, totalElements: 51, totalPages: 2, last: true };

describe("useProtocolsPage", () => {
  beforeEach(() => mockedGet.mockReset());

  it("asks the server for one page with the search term and returns the page envelope", async () => {
    mockedGet.mockResolvedValue({ data: { success: true, data: page } });
    const { result } = renderHook(() => useProtocolsPage("fever", 1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith("/api/v1/masterdata/protocols/page", {
      params: { q: "fever", page: 1, size: 50 },
    });
    expect(result.current.data?.totalElements).toBe(51);
    expect(result.current.data?.content[0].name).toBe("Fever");
  });

  it("leaves the search term out when it is blank", async () => {
    mockedGet.mockResolvedValue({ data: { success: true, data: { ...page, page: 0 } } });
    const { result } = renderHook(() => useProtocolsPage("", 0), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith("/api/v1/masterdata/protocols/page", {
      params: { q: undefined, page: 0, size: 50 },
    });
  });
});
