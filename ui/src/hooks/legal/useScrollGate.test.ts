import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";

import { useScrollGate } from "./useScrollGate";
import {
  intersectionObserverRegistry,
  triggerIntersection,
  resetIntersectionObserverMock,
} from "../../test/intersectionObserverMock";

describe("useScrollGate", () => {
  beforeEach(() => {
    resetIntersectionObserverMock();
  });

  it("hasReachedEnd is initially false", () => {
    const { result } = renderHook(() => useScrollGate());
    expect(result.current.hasReachedEnd).toBe(false);
  });

  it("stays false after triggerIntersection(false) with the sentinel attached", () => {
    const { result } = renderHook(() => useScrollGate());

    act(() => {
      result.current.sentinelRef(document.createElement("div"));
    });
    act(() => {
      triggerIntersection(false);
    });

    expect(result.current.hasReachedEnd).toBe(false);
  });

  it("becomes true after triggerIntersection(true)", () => {
    const { result } = renderHook(() => useScrollGate());

    act(() => {
      result.current.sentinelRef(document.createElement("div"));
    });
    act(() => {
      triggerIntersection(true);
    });

    expect(result.current.hasReachedEnd).toBe(true);
  });

  it("stays true (one-way ratchet) after a later triggerIntersection(false)", () => {
    const { result } = renderHook(() => useScrollGate());

    act(() => {
      result.current.sentinelRef(document.createElement("div"));
    });
    act(() => {
      triggerIntersection(true);
    });
    act(() => {
      triggerIntersection(false);
    });

    expect(result.current.hasReachedEnd).toBe(true);
  });

  it("disconnects the observer once hasReachedEnd becomes true", () => {
    const { result } = renderHook(() => useScrollGate());

    act(() => {
      result.current.sentinelRef(document.createElement("div"));
    });
    act(() => {
      triggerIntersection(true);
    });

    expect(intersectionObserverRegistry).toHaveLength(1);
    expect(intersectionObserverRegistry[0].disconnected).toBe(true);
  });
});
