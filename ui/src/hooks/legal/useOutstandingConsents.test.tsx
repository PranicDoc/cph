import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import {
  useOutstandingConsents,
  useAcceptConsent,
  OUTSTANDING_CONSENTS_QK,
} from "./useOutstandingConsents";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import api from "../../lib/axios";
import { toast } from "sonner";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mockedToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

// A fresh QueryClient per test is required — a shared client would leak
// cached outstanding data between tests, which is the exact defect T-34-03
// is about (the query hook's own gcTime:0/staleTime:0 posture guards the
// real app; this wrapper guards the test suite the same way).
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

describe("useOutstandingConsents / useAcceptConsent", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedToastError.mockReset();
  });

  it("fetches_outstanding_from_the_server_endpoint", async () => {
    const docs = [
      { id: "doc-1", consentType: "TERMS_AND_CONDITIONS", policyVersion: 1 },
      { id: "doc-2", consentType: "PRIVACY_POLICY", policyVersion: 2 },
    ];
    mockedGet.mockResolvedValue({ data: docs });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useOutstandingConsents(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(docs);
    expect(mockedGet).toHaveBeenCalledWith("/api/v1/consents/outstanding");
  });

  it("does_not_fetch_when_disabled", async () => {
    mockedGet.mockResolvedValue({ data: [] });

    const { Wrapper } = createWrapper();
    renderHook(() => useOutstandingConsents(false), { wrapper: Wrapper });

    // Give any accidental async fetch a chance to happen before asserting.
    await act(async () => {
      await Promise.resolve();
    });

    expect(mockedGet).not.toHaveBeenCalled();
  });

  it("accepts_a_consent_with_only_consent_type_and_policy_version", async () => {
    mockedPost.mockResolvedValue({ data: { id: "consent-1" } });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useAcceptConsent(), { wrapper: Wrapper });

    act(() => {
      result.current.mutate({ consentType: "PRIVACY_POLICY", policyVersion: 2 });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedPost).toHaveBeenCalledWith("/api/v1/consents", {
      consentType: "PRIVACY_POLICY",
      policyVersion: 2,
    });
    const payload = mockedPost.mock.calls[0][1];
    expect(Object.keys(payload)).toHaveLength(2);
  });

  it("re_verifies_outstanding_with_the_server_after_a_successful_accept", async () => {
    mockedGet.mockResolvedValue({
      data: [{ id: "doc-1", consentType: "PRIVACY_POLICY", policyVersion: 2 }],
    });
    mockedPost.mockResolvedValue({ data: { id: "consent-1" } });

    const { Wrapper, queryClient } = createWrapper();

    const { result: outstandingResult } = renderHook(() => useOutstandingConsents(), {
      wrapper: Wrapper,
    });
    await waitFor(() => expect(outstandingResult.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledTimes(1);

    const { result: acceptResult } = renderHook(() => useAcceptConsent(), { wrapper: Wrapper });

    act(() => {
      acceptResult.current.mutate({ consentType: "PRIVACY_POLICY", policyVersion: 2 });
    });

    await waitFor(() => expect(acceptResult.current.isSuccess).toBe(true));

    await waitFor(() => expect(mockedGet).toHaveBeenCalledTimes(2));

    // Confirm the invalidation went through this hook's own query key, not
    // some other cache entry.
    expect(
      queryClient.getQueryState(OUTSTANDING_CONSENTS_QK)?.isInvalidated
    ).toBe(false); // refetch already resolved by the time we assert here
  });

  it("toasts_the_rfc7807_detail_when_accept_fails", async () => {
    const axiosError = {
      response: { data: { detail: "Policy version is no longer current." } },
    };
    mockedPost.mockRejectedValue(axiosError);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useAcceptConsent(), { wrapper: Wrapper });

    act(() => {
      result.current.mutate({ consentType: "PRIVACY_POLICY", policyVersion: 2 });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(mockedToastError).toHaveBeenCalledWith(
      expect.stringContaining("Policy version is no longer current."),
      expect.objectContaining({ duration: 5000 })
    );
  });
});
