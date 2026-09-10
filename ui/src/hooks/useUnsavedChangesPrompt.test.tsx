import { describe, it, expect } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { useNavigate, type RouteObject } from "react-router-dom";

import { renderWithDataRouter } from "../test/renderWithDataRouter";
import { useUnsavedChangesPrompt } from "./useUnsavedChangesPrompt";

/**
 * Every assertion here runs against a LIVE data router (via
 * `renderWithDataRouter`), not a mock — `useBlocker` is actually exercised.
 *
 * The native tab-close / reload dialog is deliberately NOT asserted: jsdom
 * can neither trigger nor observe it, and it is recorded as manual-only in
 * 38-VALIDATION.md.
 */

/** Probe page: calls the hook with a locally-controllable dirty flag and
 *  exposes buttons the test drives, plus a readout of `blocker.state`. */
function Probe() {
  const [dirty, setDirty] = useState(false);
  const navigate = useNavigate();
  const blocker = useUnsavedChangesPrompt(dirty);

  return (
    <div>
      <p data-testid="blocker-state">{blocker.state}</p>
      <button type="button" onClick={() => setDirty(true)}>
        make dirty
      </button>
      <button type="button" onClick={() => setDirty(false)}>
        make clean
      </button>
      <button type="button" onClick={() => navigate("/target")}>
        go to target
      </button>
      {blocker.state === "blocked" && (
        <>
          <button type="button" onClick={() => blocker.proceed?.()}>
            proceed
          </button>
          <button type="button" onClick={() => blocker.reset?.()}>
            stay
          </button>
        </>
      )}
    </div>
  );
}

function Target() {
  return <p>TARGET PAGE</p>;
}

const routes: RouteObject[] = [
  { path: "/", element: <Probe /> },
  { path: "/target", element: <Target /> },
];

async function renderBlockedAtTarget() {
  const user = userEvent.setup();
  const view = renderWithDataRouter(routes);
  await user.click(screen.getByText("make dirty"));
  await user.click(screen.getByText("go to target"));
  await waitFor(() =>
    expect(screen.getByTestId("blocker-state").textContent).toBe("blocked")
  );
  return { user, ...view };
}

describe("useUnsavedChangesPrompt", () => {
  it("lets in-app navigation through untouched while the form is clean", async () => {
    const user = userEvent.setup();
    const { router } = renderWithDataRouter(routes);

    expect(screen.getByTestId("blocker-state").textContent).toBe("unblocked");

    await user.click(screen.getByText("go to target"));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/target")
    );
    expect(screen.getByText("TARGET PAGE")).toBeInTheDocument();
    expect(screen.queryByTestId("blocker-state")).not.toBeInTheDocument();
  });

  it("blocks in-app navigation while the form is dirty", async () => {
    const user = userEvent.setup();
    const { router } = renderWithDataRouter(routes);

    await user.click(screen.getByText("make dirty"));
    await user.click(screen.getByText("go to target"));

    await waitFor(() =>
      expect(screen.getByTestId("blocker-state").textContent).toBe("blocked")
    );
    expect(router.state.location.pathname).toBe("/");
    expect(screen.queryByText("TARGET PAGE")).not.toBeInTheDocument();
  });

  it("completes the navigation when proceed() is called from the blocked state", async () => {
    const { user, router } = await renderBlockedAtTarget();

    await user.click(screen.getByText("proceed"));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/target")
    );
    expect(screen.getByText("TARGET PAGE")).toBeInTheDocument();
  });

  it("stays on the current route and returns to unblocked when reset() is called", async () => {
    const { user, router } = await renderBlockedAtTarget();

    await user.click(screen.getByText("stay"));

    await waitFor(() =>
      expect(screen.getByTestId("blocker-state").textContent).toBe("unblocked")
    );
    expect(router.state.location.pathname).toBe("/");
    expect(screen.queryByText("TARGET PAGE")).not.toBeInTheDocument();
  });

  it("auto-resets a pending block when the form flips from dirty to clean", async () => {
    // Regression guard for 38-RESEARCH Pitfall 8: a successful save clears
    // dirty state, and the guard must not strand the app on the form or
    // intercept its own post-save redirect.
    const { user, router } = await renderBlockedAtTarget();

    await user.click(screen.getByText("make clean"));

    await waitFor(() =>
      expect(screen.getByTestId("blocker-state").textContent).toBe("unblocked")
    );
    expect(router.state.location.pathname).toBe("/");
  });
});
