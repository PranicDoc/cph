import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";

import { useReadingSessionLifecycle } from "./useReadingSession";

vi.mock("../../lib/axios", () => ({
  default: { post: vi.fn(), patch: vi.fn() },
}));

import api from "../../lib/axios";

const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mockedPatch = api.patch as unknown as ReturnType<typeof vi.fn>;

let originalSendBeacon: Navigator["sendBeacon"] | undefined;

function useHarness(hasReachedEnd: boolean) {
  const hasReachedEndRef = React.useRef(hasReachedEnd);
  hasReachedEndRef.current = hasReachedEnd;
  return useReadingSessionLifecycle({
    consentType: "PRIVACY_POLICY",
    policyVersion: 1,
    hasReachedEndRef,
  });
}

describe("useReadingSessionLifecycle", () => {
  beforeEach(() => {
    mockedPost.mockReset();
    mockedPatch.mockReset();
    // Default resolved value so renderHook's automatic unmount-on-cleanup
    // (which fires closeSession) never sees `.catch` called on `undefined`
    // in tests that don't care about the close call themselves.
    mockedPatch.mockResolvedValue({ data: {} });
    originalSendBeacon = navigator.sendBeacon;
    navigator.sendBeacon = vi.fn();
  });

  afterEach(() => {
    // Restore only navigator.sendBeacon — NOT vi.restoreAllMocks(), which
    // would strip the vi.mock("../../lib/axios") implementation out from
    // under React Testing Library's own automatic unmount-cleanup afterEach
    // hook, which can run after this one.
    navigator.sendBeacon = originalSendBeacon as Navigator["sendBeacon"];
  });

  it("opens_a_session_on_mount_with_consentTypeAndPolicyVersion", async () => {
    mockedPost.mockResolvedValue({ data: { id: "sess-1" } });

    const { result } = renderHook(() => useHarness(false));

    await waitFor(() => expect(result.current.sessionId).toBe("sess-1"));

    expect(mockedPost).toHaveBeenCalledTimes(1);
    expect(mockedPost).toHaveBeenCalledWith(
      "/api/v1/consents/reading-sessions",
      expect.objectContaining({ consentType: "PRIVACY_POLICY", policyVersion: 1 })
    );
  });

  it("does_not_open_a_second_session_when_remounted_in_strict_mode", async () => {
    mockedPost.mockResolvedValue({ data: { id: "sess-1" } });

    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(React.StrictMode, null, children);

    const { result } = renderHook(() => useHarness(false), { wrapper });

    await waitFor(() => expect(result.current.sessionId).toBe("sess-1"));

    expect(mockedPost).toHaveBeenCalledTimes(1);
  });

  it("closes_via_axios_patch_on_unmount", async () => {
    mockedPost.mockResolvedValue({ data: { id: "sess-1" } });
    mockedPatch.mockResolvedValue({ data: {} });

    const { result, unmount } = renderHook(() => useHarness(true));

    await waitFor(() => expect(result.current.sessionId).toBe("sess-1"));

    unmount();

    expect(mockedPatch).toHaveBeenCalledTimes(1);
    expect(mockedPatch).toHaveBeenCalledWith(
      "/api/v1/consents/reading-sessions/sess-1/close",
      expect.objectContaining({ scrollCompleted: true })
    );
    expect(navigator.sendBeacon).not.toHaveBeenCalled();
  });

  it("closes_via_sendBeacon_when_the_document_becomes_hidden", async () => {
    mockedPost.mockResolvedValue({ data: { id: "sess-1" } });

    const { result } = renderHook(() => useHarness(false));

    await waitFor(() => expect(result.current.sessionId).toBe("sess-1"));

    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => "hidden",
    });

    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(navigator.sendBeacon).toHaveBeenCalledTimes(1);
    const [url] = (navigator.sendBeacon as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/cph/api/v1/consents/reading-sessions/sess-1/close");
    expect(mockedPatch).not.toHaveBeenCalled();
  });

  it("closes_only_once_when_hidden_then_unmounted", async () => {
    mockedPost.mockResolvedValue({ data: { id: "sess-1" } });

    const { result, unmount } = renderHook(() => useHarness(false));

    await waitFor(() => expect(result.current.sessionId).toBe("sess-1"));

    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => "hidden",
    });

    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    unmount();

    const beaconCalls = (navigator.sendBeacon as ReturnType<typeof vi.fn>).mock.calls.length;
    const patchCalls = mockedPatch.mock.calls.length;
    expect(beaconCalls + patchCalls).toBe(1);
  });
});
