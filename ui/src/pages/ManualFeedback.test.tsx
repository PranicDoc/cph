import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import ManualFeedback from "./ManualFeedback";
import { renderWithDataRouter } from "../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
const view = vi.hoisted(() => ({ isNewView: false }));
vi.mock("@/ui-version", () => ({ useUiVersion: () => view }));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "@/lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  mGet.mockResolvedValue({ data: { content: [{ caseSymptomId: "s1", name: "Headache" }] } });
  return renderWithDataRouter(
    [
      { path: "/cases/:healingCaseId/feedback", element: <ManualFeedback /> },
      { path: "/patients", element: <div>patients-page</div> },
    ],
    { initialEntries: ["/cases/case-1/feedback"] }
  );
}

/** Moves the first symptom's slider up from 0 by `steps` (keyboard, as Radix's slider supports). */
async function rateSeverity(user: ReturnType<typeof userEvent.setup>, steps: number) {
  const thumb = await screen.findByRole("slider");
  thumb.focus();
  await user.keyboard("{ArrowRight}".repeat(steps));
}

describe("ManualFeedback page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    view.isNewView = false;
  });

  it("shows a severity slider per symptom and an empty healing date, and refuses to save without one", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { name: "Patient feedback" })).toBeInTheDocument();
    expect(screen.getByLabelText("Healing date")).toHaveValue("");
    await rateSeverity(user, 2);
    await user.click(screen.getByRole("button", { name: "Save feedback" }));

    expect(screen.getByText("Enter the healing date.")).toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();
  });

  it("posts the ratings, comment, type and healing date for the case, then returns to Patients", async () => {
    mPost.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    const { router } = renderPage();

    await user.type(screen.getByLabelText("Healing date"), "2026-10-20");
    await user.click(screen.getByRole("button", { name: "Final" }));
    await rateSeverity(user, 2);
    await user.type(screen.getByLabelText("Comments"), "Sleeping better");
    await user.click(screen.getByRole("button", { name: "Save feedback" }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith("/api/v1/cases/case-1/feedback", {
        feedbackType: "FINAL",
        healingDate: "2026-10-20",
        // Severity 2 of 10 (higher is worse) is stored as rating 8 (higher is better).
        symptoms: [{ caseSymptomId: "s1", rating: 8, notes: "" }],
        feedbackText: "Sleeping better",
      })
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients"));
  }, 15_000);

  it("explains when feedback for that healing is already recorded, and stays on the page", async () => {
    mPost.mockRejectedValue({
      response: { data: { type: "https://pranicdoc.com/errors/feedback-already-submitted" } },
    });
    const user = userEvent.setup();
    const { router } = renderPage();

    await user.type(screen.getByLabelText("Healing date"), "2026-10-20");
    await user.type(screen.getByLabelText("Comments"), "Same as before");
    await user.click(screen.getByRole("button", { name: "Save feedback" }));

    expect(await screen.findByText("Feedback for this healing is already recorded.")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/cases/case-1/feedback");
  }, 15_000);

  it("renders inside the new view's styles, where its severity slider lives", async () => {
    view.isNewView = true;
    const { container } = renderPage();

    const slider = await screen.findByRole("slider", { name: /Headache/ });
    expect(container.querySelector(".hlx")).toContainElement(slider);
  });
});
