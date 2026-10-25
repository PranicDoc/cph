import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import PatientFeedback from "./Feedback";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import api from "../lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

function renderForm(isFinal: boolean) {
  // As after the link's first load: token and case already moved into sessionStorage.
  sessionStorage.setItem("feedbackToken", "tok");
  sessionStorage.setItem("feedbackCaseId", "case-1");
  sessionStorage.setItem("isFinalFeedback", String(isFinal));
  mPost.mockImplementation((url: string) =>
    Promise.resolve({ data: url.endsWith("init-feedback-session") ? { healingDate: "2026-10-08" } : {} })
  );
  mGet.mockResolvedValue({ data: { content: [{ caseSymptomId: "s1", name: "Headache" }] } });
  render(
    <MemoryRouter initialEntries={["/feedback"]}>
      <PatientFeedback />
    </MemoryRouter>
  );
}

/** Moves the first symptom's slider up from 0 by `steps` (keyboard, as Radix's slider supports). */
async function rateSeverity(user: ReturnType<typeof userEvent.setup>, steps: number) {
  const thumb = await screen.findByRole("slider");
  thumb.focus();
  await user.keyboard("{ArrowRight}".repeat(steps));
}

describe("Patient feedback form (WhatsApp link)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it("shows the healing date and records an interim comment", async () => {
    const user = userEvent.setup();
    renderForm(false);

    expect(await screen.findByText(new Date("2026-10-08T00:00:00").toLocaleDateString())).toBeInTheDocument();
    await rateSeverity(user, 2);
    await user.type(screen.getByPlaceholderText("Type your feedback here..."), "Less pain");
    await user.click(screen.getByRole("button", { name: /submit feedback/i }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith("/api/v1/sessions/case-1/feedback/qualitative", {
        feedbackType: "INTERIM",
        feedbackText: "Less pain",
        patientRemarks: "",
      })
    );
    expect(mPost).toHaveBeenCalledWith("/api/v1/sessions/case-1/feedback/symptoms", [
      { caseSymptomId: "s1", rating: 8, notes: "" },
    ]);
  }, 15_000);

  it("records a final comment as FINAL", async () => {
    const user = userEvent.setup();
    renderForm(true);

    await rateSeverity(user, 1);
    await user.type(screen.getByPlaceholderText("Type your feedback here..."), "All good");
    await user.click(screen.getByRole("button", { name: /submit feedback/i }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith(
        "/api/v1/sessions/case-1/feedback/qualitative",
        expect.objectContaining({ feedbackType: "FINAL", feedbackText: "All good" })
      )
    );
  }, 15_000);
});
