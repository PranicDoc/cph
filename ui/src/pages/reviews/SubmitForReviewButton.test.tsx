import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { SubmitForReviewButton } from "./SubmitForReviewButton";
import api from "../../lib/axios";
import { toast } from "sonner";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() } }));

const BLOCKED =
  "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";

function renderButton(status: Parameters<typeof SubmitForReviewButton>[0]["status"]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SubmitForReviewButton caseId="case-1" status={status} />
    </QueryClientProvider>
  );
}

describe("SubmitForReviewButton", () => {
  beforeEach(() => vi.clearAllMocks());

  it("submitBlocked_showsVerbatimMessage when no instructor is linked", async () => {
    const user = userEvent.setup();
    renderButton("NOT_SUBMITTED_NO_REVIEWER");

    await user.click(screen.getByRole("button", { name: /submit for review/i }));

    expect(screen.getByRole("alert")).toHaveTextContent(BLOCKED);
    expect(api.post).not.toHaveBeenCalled();
  });

  it("submitBlocked_showsVerbatimMessage when the server refuses with it", async () => {
    vi.mocked(api.post).mockRejectedValue({ response: { status: 409, data: { detail: BLOCKED } } });
    const user = userEvent.setup();
    renderButton("NOT_SUBMITTED");

    await user.click(screen.getByRole("button", { name: /submit for review/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(BLOCKED);
  });

  it("posts the submit for the case", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderButton("DISMISSED");

    await user.click(screen.getByRole("button", { name: /submit for review/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/cases/case-1/submit"));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("says nothing changed when the server reports an unchanged case", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { outcome: "UNCHANGED", latestRevisionNo: 3 } });
    const user = userEvent.setup();
    renderButton("PENDING_REVIEW");

    await user.click(screen.getByRole("button", { name: /resubmit for review/i }));

    await waitFor(() =>
      expect(toast.info).toHaveBeenCalledWith(
        "Nothing has changed since version 3, so there is nothing new to review."
      )
    );
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("says review was requested again on a re-request", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { outcome: "REREQUESTED", latestRevisionNo: 2 } });
    const user = userEvent.setup();
    renderButton("CHANGES_REQUESTED");

    await user.click(screen.getByRole("button", { name: /resubmit for review/i }));

    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("Review requested again."));
  });

  it("keeps the usual message when a revision was added", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { outcome: "REVISION_ADDED", latestRevisionNo: 2 } });
    const user = userEvent.setup();
    renderButton("CHANGES_REQUESTED");

    await user.click(screen.getByRole("button", { name: /resubmit for review/i }));

    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("Submitted for review"));
  });

  it("offers a resubmit while a review is open", () => {
    renderButton("CHANGES_REQUESTED");
    expect(screen.getByRole("button", { name: /resubmit for review/i })).toBeInTheDocument();
  });

  it("renders nothing for an approved case", () => {
    const { container } = renderButton("APPROVED");
    expect(container).toBeEmptyDOMElement();
  });
});
