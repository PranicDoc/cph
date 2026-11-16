import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";

import { ReviewStatusBadge } from "./ReviewStatusBadge";
import type { CaseReviewStatus } from "../../types/review";

describe("ReviewStatusBadge", () => {
  it("badgeLabelsPerStatus", () => {
    const labels: Record<CaseReviewStatus, string> = {
      NOT_SUBMITTED: "Not submitted",
      NOT_SUBMITTED_NO_REVIEWER: "Not submitted — no instructor linked",
      PENDING_REVIEW: "In review",
      CHANGES_REQUESTED: "Changes requested",
      APPROVED: "Approved",
      DISMISSED: "Dismissed — submit again",
    };
    for (const [status, label] of Object.entries(labels)) {
      const { unmount } = render(<ReviewStatusBadge status={status as CaseReviewStatus} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });
});
