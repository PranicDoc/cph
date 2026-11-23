import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import api from "../lib/axios";
import { useApprovedEditGuard } from "./useApprovedEditGuard";

vi.mock("./useCaseReviewEnabled", () => ({ useCaseReviewEnabled: () => true }));
vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

const POPUP = "This case is approved. Editing it will dismiss the approval and you will need a new review.";

function Editor({ caseId, onEdit }: { caseId: string; onEdit: () => void }) {
  const { guard, dialog, isApproved } = useApprovedEditGuard(caseId);
  return (
    <>
      {isApproved && <span>approved-loaded</span>}
      <button type="button" onClick={() => guard(onEdit)}>
        Edit case
      </button>
      {dialog}
    </>
  );
}

function renderEditor(status: string, onEdit = vi.fn()) {
  vi.mocked(api.get).mockResolvedValue({
    data: [{ caseId: "c1", status, eligible: true, latestRevisionNo: 1 }],
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <Editor caseId="c1" onEdit={onEdit} />
    </QueryClientProvider>
  );
  return onEdit;
}

describe("useApprovedEditGuard", () => {
  beforeEach(() => vi.clearAllMocks());

  it("approvedCaseEdit_showsPopup_cancelKeepsApproval", async () => {
    const user = userEvent.setup();
    const onEdit = renderEditor("APPROVED");
    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/case-statuses", { params: { caseIds: "c1" } })
    );
    await screen.findByText("approved-loaded");

    await user.click(screen.getByRole("button", { name: "Edit case" }));
    expect(await screen.findByText(POPUP)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByText(POPUP)).not.toBeInTheDocument());
    // Cancel edits nothing, so nothing reaches the server and the approval stands.
    expect(onEdit).not.toHaveBeenCalled();
    expect(api.post).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Edit case" }));
    await user.click(await screen.findByRole("button", { name: "Edit and dismiss approval" }));
    expect(onEdit).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.queryByText(POPUP)).not.toBeInTheDocument());

    // Confirmed once on this page: the next edit goes straight through.
    await user.click(screen.getByRole("button", { name: "Edit case" }));
    expect(onEdit).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(POPUP)).not.toBeInTheDocument();
  });

  it("a case that is not approved is edited without a popup", async () => {
    const user = userEvent.setup();
    const onEdit = renderEditor("PENDING_REVIEW");
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: "Edit case" }));

    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(POPUP)).not.toBeInTheDocument();
  });
});
