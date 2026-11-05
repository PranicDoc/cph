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

let sessions: { id: string; sessionNumber: number; sessionDate: string }[] = [];
let recorded: unknown = { interims: [], finalFeedbackText: null };

function renderPage() {
  mGet.mockImplementation((url: string) =>
    Promise.resolve(
      url.startsWith("/api/v1/sessions/case/")
        ? { data: sessions }
        : url === "/api/v1/cases/case-1/feedback"
          ? { data: recorded }
          : { data: { content: [{ caseSymptomId: "s1", name: "Headache" }] } }
    )
  );
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
    sessions = [];
    recorded = { interims: [], finalFeedbackText: null };
  });

  const twoSessions = () => [
    { id: "sess-1", sessionNumber: 1, sessionDate: "2026-10-01" },
    { id: "sess-2", sessionNumber: 2, sessionDate: "2026-10-08" },
  ];

  it("shows the picked session's date read-only, and refuses to save interim feedback without a session", async () => {
    sessions = twoSessions();
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { name: "Patient feedback" })).toBeInTheDocument();
    await rateSeverity(user, 2);
    await user.click(screen.getByRole("button", { name: "Save feedback" }));
    expect(screen.getByText("Pick the session this feedback is for.")).toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();

    await user.click(await screen.findByLabelText("Session"));
    await user.click(await screen.findByRole("option", { name: /Session 2/ }));
    const date = screen.getByLabelText("Healing date");
    expect(date).toHaveValue("2026-10-08");
    expect(date).toBeDisabled();
  });

  it("posts an interim's ratings, comment and session, then returns to Patients", async () => {
    sessions = twoSessions();
    mPost.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    const { router } = renderPage();

    await user.click(await screen.findByLabelText("Session"));
    await user.click(await screen.findByRole("option", { name: /Session 1/ }));
    await rateSeverity(user, 2);
    await user.type(screen.getByLabelText("Comments"), "Sleeping better");
    await user.click(screen.getByRole("button", { name: "Save feedback" }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith("/api/v1/cases/case-1/feedback", {
        feedbackType: "INTERIM",
        sessionId: "sess-1",
        // Severity 2 of 10 (higher is worse) is stored as rating 8 (higher is better).
        symptoms: [{ caseSymptomId: "s1", rating: 8, notes: "" }],
        feedbackText: "Sleeping better",
      })
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients"));
  }, 15_000);

  it("final feedback is a comment on the latest session: no session picker, no ratings, date shown", async () => {
    sessions = twoSessions();
    mPost.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Final" }));
    expect(screen.queryByLabelText("Session")).not.toBeInTheDocument();
    expect(screen.queryByRole("slider")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText("Healing date")).toHaveValue("2026-10-08"));

    await user.click(screen.getByRole("button", { name: "Save feedback" }));
    expect(screen.getByText("Add the final feedback comment.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Comments"), "Fully recovered");
    await user.click(screen.getByRole("button", { name: "Save feedback" }));
    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith("/api/v1/cases/case-1/feedback", {
        feedbackType: "FINAL",
        symptoms: [],
        feedbackText: "Fully recovered",
      })
    );
  }, 15_000);

  it("explains when feedback for that healing is already recorded, and stays on the page", async () => {
    sessions = twoSessions();
    mPost.mockRejectedValue({
      response: { data: { type: "https://pranicdoc.com/errors/feedback-already-submitted" } },
    });
    const user = userEvent.setup();
    const { router } = renderPage();

    await user.click(await screen.findByLabelText("Session"));
    await user.click(await screen.findByRole("option", { name: /Session 1/ }));
    await user.type(screen.getByLabelText("Comments"), "Same as before");
    await user.click(screen.getByRole("button", { name: "Save feedback" }));

    expect(await screen.findByText("Feedback for this healing is already recorded.")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/cases/case-1/feedback");
  }, 15_000);

  it("opens a session's recorded interim for editing, and saves the change", async () => {
    sessions = twoSessions();
    recorded = {
      interims: [{ sessionId: "sess-1", feedbackText: "Old note", ratings: [{ caseSymptomId: "s1", rating: 8 }] }],
      finalFeedbackText: null,
    };
    mPost.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByLabelText("Session"));
    await user.click(await screen.findByRole("option", { name: /Session 1/ }));
    const comments = screen.getByLabelText("Comments");
    await waitFor(() => expect(comments).toHaveValue("Old note"));

    await user.clear(comments);
    await user.type(comments, "New note");
    await user.click(screen.getByRole("button", { name: "Update feedback" }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith("/api/v1/cases/case-1/feedback", {
        feedbackType: "INTERIM",
        sessionId: "sess-1",
        // The stored rating 8 came back as severity 2 and goes out unchanged.
        symptoms: [{ caseSymptomId: "s1", rating: 8, notes: "" }],
        feedbackText: "New note",
      })
    );
  }, 15_000);

  it("a session with nothing recorded starts empty", async () => {
    sessions = twoSessions();
    recorded = {
      interims: [{ sessionId: "sess-1", feedbackText: "Old note", ratings: [] }],
      finalFeedbackText: null,
    };
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByLabelText("Session"));
    await user.click(await screen.findByRole("option", { name: /Session 2/ }));

    expect(screen.getByLabelText("Comments")).toHaveValue("");
    expect(screen.getByRole("button", { name: "Save feedback" })).toBeInTheDocument();
  }, 15_000);

  it("opens the recorded final feedback for editing", async () => {
    sessions = twoSessions();
    recorded = { interims: [], finalFeedbackText: "Fully recovered" };
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Final" }));

    await waitFor(() => expect(screen.getByLabelText("Comments")).toHaveValue("Fully recovered"));
    expect(screen.getByRole("button", { name: "Update feedback" })).toBeInTheDocument();
  }, 15_000);

  it("limits the comment by feedback type, shows the count, and has no example text", async () => {
    const user = userEvent.setup();
    renderPage();

    const comments = await screen.findByLabelText("Comments");
    expect(comments).toHaveAttribute("maxlength", "400");
    expect(comments).not.toHaveAttribute("placeholder");
    expect(screen.getByText("0/400")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Final" }));
    expect(comments).toHaveAttribute("maxlength", "1500");
    expect(screen.getByText("0/1500")).toBeInTheDocument();
  });

  it("older text over the limit must be shortened before it saves", async () => {
    sessions = twoSessions();
    recorded = { interims: [], finalFeedbackText: "x".repeat(1501) };
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Final" }));
    await waitFor(() => expect(screen.getByText("1501/1500")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Update feedback" }));

    expect(screen.getByText("Shorten the comment to 1500 characters.")).toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();
  }, 15_000);

  it("renders inside the new view's styles, where its severity slider lives", async () => {
    view.isNewView = true;
    const { container } = renderPage();

    const slider = await screen.findByRole("slider", { name: /Headache/ });
    expect(container.querySelector(".hlx")).toContainElement(slider);
  });
});
