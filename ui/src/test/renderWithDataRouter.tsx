import { render, type RenderResult } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryRouter,
  RouterProvider,
  type RouteObject,
} from "react-router-dom";

interface RenderWithDataRouterOptions {
  initialEntries?: string[];
}

/**
 * Mounts `routes` in a live react-router v7 **data router**
 * (`createMemoryRouter` + `<RouterProvider>`) so data APIs such as
 * `useBlocker` actually run instead of throwing "useBlocker must be used
 * within a data router". Use this — not `MemoryRouter` + `<Routes>` — for any
 * component or hook that touches the discard-unsaved-changes guard.
 *
 * A **fresh** `QueryClient` is created per call (never at module scope): a
 * shared client would leak cached query data between tests, exactly the class
 * of defect the per-test isolation guards against. `retry: false` keeps failed
 * queries from hanging the test.
 *
 * The returned object is the testing-library render result spread with
 * `{ router }`, so a test can assert `router.state.location.pathname`.
 */
export function renderWithDataRouter(
  routes: RouteObject[],
  options: RenderWithDataRouterOptions = {}
): RenderResult & { router: ReturnType<typeof createMemoryRouter> } {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  const router = createMemoryRouter(routes, {
    initialEntries: options.initialEntries ?? ["/"],
  });

  const result = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );

  return { ...result, router };
}
