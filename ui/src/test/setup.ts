import "@testing-library/jest-dom/vitest";

import { installIntersectionObserverMock } from "./intersectionObserverMock";

// jsdom doesn't implement these — Radix components (Popover/Select/Command)
// call them during pointer interaction and scroll-into-view handling.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {};
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// jsdom doesn't implement matchMedia — used by useIsMobile.
if (!window.matchMedia) {
  window.matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }) as unknown as MediaQueryList;
}

// jsdom doesn't implement ResizeObserver — Radix's Popper positioning uses it.
if (!("ResizeObserver" in window)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  // @ts-expect-error - test-only stub
  window.ResizeObserver = ResizeObserverStub;
}

// jsdom doesn't implement IntersectionObserver — the Phase 33 scroll gate uses
// it. Installed unconditionally (NOT gated behind an `if (!("IntersectionObserver"
// in window))` check like the ResizeObserver stub above): the scroll-gate tests
// need a *controllable* double even if a future jsdom ships a real
// implementation, because a real implementation would never fire in a
// zero-layout jsdom environment (see 33-RESEARCH.md Pitfall 1).
installIntersectionObserverMock();
