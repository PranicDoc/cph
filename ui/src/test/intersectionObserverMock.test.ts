import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  intersectionObserverRegistry,
  resetIntersectionObserverMock,
  triggerIntersection,
} from "./intersectionObserverMock";

// The mock is already installed globally by setup.ts at this point, so we
// construct `new IntersectionObserver(...)` directly rather than calling
// `installIntersectionObserverMock()` again.

describe("intersectionObserverMock", () => {
  beforeEach(() => {
    resetIntersectionObserverMock();
  });

  it("registers an observed target after observe()", () => {
    const el = document.createElement("div");
    const cb = vi.fn();
    const observer = new IntersectionObserver(cb);
    observer.observe(el);

    expect(intersectionObserverRegistry).toHaveLength(1);
    expect(intersectionObserverRegistry[0].targets).toContain(el);
  });

  it("triggerIntersection(false) invokes the callback with isIntersecting: false", () => {
    const el = document.createElement("div");
    const cb = vi.fn();
    const observer = new IntersectionObserver(cb);
    observer.observe(el);

    triggerIntersection(false);

    expect(cb).toHaveBeenCalledTimes(1);
    const [entries] = cb.mock.calls[0];
    expect(entries[0].isIntersecting).toBe(false);
    expect(entries[0].target).toBe(el);
  });

  it("triggerIntersection(true) invokes the callback with isIntersecting: true", () => {
    const el = document.createElement("div");
    const cb = vi.fn();
    const observer = new IntersectionObserver(cb);
    observer.observe(el);

    triggerIntersection(true);

    expect(cb).toHaveBeenCalledTimes(1);
    const [entries] = cb.mock.calls[0];
    expect(entries[0].isIntersecting).toBe(true);
  });

  it("does not invoke the callback after disconnect()", () => {
    const el = document.createElement("div");
    const cb = vi.fn();
    const observer = new IntersectionObserver(cb);
    observer.observe(el);
    observer.disconnect();
    // Re-observe after disconnect to isolate the `disconnected` guard from
    // disconnect()'s targets-clearing side effect: if only the empty-targets
    // check gated delivery, this re-added target would make the callback
    // fire again. The `disconnected` flag must independently stop it.
    observer.observe(el);

    triggerIntersection(true);

    expect(cb).not.toHaveBeenCalled();
  });

  it("resetIntersectionObserverMock() empties the registry", () => {
    const el = document.createElement("div");
    const observer = new IntersectionObserver(vi.fn());
    observer.observe(el);

    expect(intersectionObserverRegistry).toHaveLength(1);

    resetIntersectionObserverMock();

    expect(intersectionObserverRegistry).toHaveLength(0);
  });
});
