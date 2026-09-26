import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import api from "../../lib/axios";
import { toast } from "sonner";
import { useProtocolImportJobs, useSubmitProtocolImport, type ProtocolImportJobView } from "./useMasterData";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;

const PROTOCOL_QK = ["master-protocols"];
const CHAKRA_QK = ["master-chakras"];
const COLOR_QK = ["master-colors"];
const IMPORT_JOBS_QK = ["master-protocol-import-jobs"];

function jobStub(overrides: Partial<ProtocolImportJobView> = {}): ProtocolImportJobView {
  return {
    id: "job-1",
    originalFilename: "protocols.xlsx",
    status: "QUEUED",
    totalRows: null,
    created: null,
    updated: null,
    skipped: null,
    errors: [],
    failureMessage: null,
    startedAt: null,
    completedAt: null,
    createdAt: "2026-09-19T00:00:00Z",
    ...overrides,
  };
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function wrapperFor(queryClient: QueryClient) {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe("useProtocolImportJobs", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    vi.clearAllMocks();
  });

  it("reads GET /api/v1/masterdata/protocols/import-jobs and unwraps data.data", async () => {
    mockedGet.mockResolvedValue({ data: { data: [jobStub()] } });
    const { result } = renderHook(() => useProtocolImportJobs(), {
      wrapper: wrapperFor(makeClient()),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedGet).toHaveBeenCalledWith("/api/v1/masterdata/protocols/import-jobs");
    expect(result.current.data).toEqual([jobStub()]);
  });

  it("does not invalidate protocol/chakra/color queries while every job is still in flight", async () => {
    mockedGet.mockResolvedValue({
      data: { data: [jobStub({ status: "QUEUED" }), jobStub({ id: "job-2", status: "PROCESSING" })] },
    });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useProtocolImportJobs(), { wrapper: wrapperFor(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: PROTOCOL_QK }));
    expect(invalidateSpy).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: CHAKRA_QK }));
    expect(invalidateSpy).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: COLOR_QK }));
  });

  it("invalidates protocol/chakra/color queries once a job is COMPLETED", async () => {
    mockedGet.mockResolvedValue({ data: { data: [jobStub({ status: "COMPLETED" })] } });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useProtocolImportJobs(), { wrapper: wrapperFor(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: PROTOCOL_QK }));
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: CHAKRA_QK }));
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: COLOR_QK }));
  });

  it("invalidates protocol/chakra/color queries once a job is FAILED", async () => {
    mockedGet.mockResolvedValue({
      data: { data: [jobStub({ status: "FAILED", failureMessage: "boom" })] },
    });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useProtocolImportJobs(), { wrapper: wrapperFor(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: PROTOCOL_QK }));
  });

  it("keeps polling every 2s while a job is QUEUED or PROCESSING", async () => {
    mockedGet.mockResolvedValue({ data: { data: [jobStub({ status: "PROCESSING" })] } });
    vi.useFakeTimers();
    try {
      const { result } = renderHook(() => useProtocolImportJobs(), {
        wrapper: wrapperFor(makeClient()),
      });

      await act(async () => {
        await vi.waitFor(() => expect(result.current.isSuccess).toBe(true), { timeout: 5000 });
      });
      expect(mockedGet).toHaveBeenCalledTimes(1);

      await act(async () => {
        vi.advanceTimersByTime(2000);
        await Promise.resolve();
      });

      expect(mockedGet.mock.calls.length).toBeGreaterThanOrEqual(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("stops polling once every job is terminal", async () => {
    mockedGet.mockResolvedValue({ data: { data: [jobStub({ status: "COMPLETED" })] } });
    vi.useFakeTimers();
    try {
      const { result } = renderHook(() => useProtocolImportJobs(), {
        wrapper: wrapperFor(makeClient()),
      });

      await act(async () => {
        await vi.waitFor(() => expect(result.current.isSuccess).toBe(true), { timeout: 5000 });
      });
      const callsAfterFirstFetch = mockedGet.mock.calls.length;

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
      });

      expect(mockedGet.mock.calls.length).toBe(callsAfterFirstFetch);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("useSubmitProtocolImport", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    vi.clearAllMocks();
  });

  it("posts the file as multipart/form-data and unwraps the returned job", async () => {
    const file = new File(["a,b,c"], "protocols.xlsx");
    mockedPost.mockResolvedValue({ data: { data: jobStub({ id: "job-9" }) } });

    const { result } = renderHook(() => useSubmitProtocolImport(), {
      wrapper: wrapperFor(makeClient()),
    });

    let returned: ProtocolImportJobView | undefined;
    await act(async () => {
      returned = await result.current.mutateAsync(file);
    });

    expect(mockedPost).toHaveBeenCalledTimes(1);
    const [url, formData, config] = mockedPost.mock.calls[0];
    expect(url).toBe("/api/v1/masterdata/protocols/import");
    expect(formData).toBeInstanceOf(FormData);
    expect(config).toMatchObject({ headers: { "Content-Type": undefined } });
    expect(returned).toEqual(jobStub({ id: "job-9" }));
    expect(toast.success).toHaveBeenCalledWith("Import queued");
  });

  it("invalidates the import-jobs query on success, not the protocol/chakra/color queries", async () => {
    mockedPost.mockResolvedValue({ data: { data: jobStub() } });
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useSubmitProtocolImport(), { wrapper: wrapperFor(client) });

    await act(async () => {
      await result.current.mutateAsync(new File(["x"], "p.csv"));
    });

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: IMPORT_JOBS_QK }));
    expect(invalidateSpy).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: PROTOCOL_QK }));
  });

  it("shows an error toast and does not throw when the request fails", async () => {
    mockedPost.mockRejectedValue({ response: { data: { detail: "File too large" } } });

    const { result } = renderHook(() => useSubmitProtocolImport(), {
      wrapper: wrapperFor(makeClient()),
    });

    await act(async () => {
      await expect(result.current.mutateAsync(new File(["x"], "p.csv"))).rejects.toBeTruthy();
    });

    expect(toast.error).toHaveBeenCalledWith("File too large", { duration: 5000 });
  });
});
