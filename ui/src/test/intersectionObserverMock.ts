// Controllable IntersectionObserver test double.
//
// jsdom implements neither IntersectionObserver nor real box layout, so a
// naive stub that hardcodes `isIntersecting: true` would make every
// scroll-gate test tautological — it would pass even if the gate logic
// under test were deleted. This double lets a test deterministically drive
// `isIntersecting` true or false via `triggerIntersection(...)`, and proves
// disconnect actually stops delivery (see intersectionObserverMock.test.ts).

export interface RegisteredObserver {
  callback: IntersectionObserverCallback;
  instance: IntersectionObserver;
  targets: Element[];
  disconnected: boolean;
}

export const intersectionObserverRegistry: RegisteredObserver[] = [];

export function installIntersectionObserverMock(): void {
  class IntersectionObserverMock implements IntersectionObserver {
    root: Element | Document | null = null;
    rootMargin = "0px";
    thresholds: ReadonlyArray<number> = [0];

    private readonly callback: IntersectionObserverCallback;

    constructor(callback: IntersectionObserverCallback) {
      this.callback = callback;
      intersectionObserverRegistry.push({
        callback,
        instance: this,
        targets: [],
        disconnected: false,
      });
    }

    private entry(): RegisteredObserver {
      const found = intersectionObserverRegistry.find((e) => e.instance === this);
      if (!found) {
        throw new Error("IntersectionObserverMock: registry entry missing for instance");
      }
      return found;
    }

    observe(node: Element): void {
      this.entry().targets.push(node);
    }

    unobserve(node: Element): void {
      const entry = this.entry();
      entry.targets = entry.targets.filter((t) => t !== node);
    }

    disconnect(): void {
      const entry = this.entry();
      entry.disconnected = true;
      entry.targets = [];
    }

    takeRecords(): IntersectionObserverEntry[] {
      return [];
    }
  }

  window.IntersectionObserver = IntersectionObserverMock;
  globalThis.IntersectionObserver = IntersectionObserverMock;
}

export function triggerIntersection(isIntersecting: boolean): void {
  for (const entry of intersectionObserverRegistry) {
    if (entry.disconnected || entry.targets.length === 0) continue;

    const entries = entry.targets.map((target) => ({
      isIntersecting,
      target,
      intersectionRatio: isIntersecting ? 1 : 0,
      time: 0,
    }));

    entry.callback(entries as unknown as IntersectionObserverEntry[], entry.instance);
  }
}

export function resetIntersectionObserverMock(): void {
  intersectionObserverRegistry.length = 0;
}
