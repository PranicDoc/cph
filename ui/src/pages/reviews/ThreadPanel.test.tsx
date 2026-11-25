import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

import ThreadPanel from "./ThreadPanel";
import { formatWhen } from "./ui";
import type { ReviewComment } from "../../types/review";

const comment = (over: Partial<ReviewComment>): ReviewComment => ({
  id: "c1",
  submissionId: "s1",
  revisionNo: 1,
  parentId: null,
  authorId: "a1",
  authorName: "Ravi Menon",
  authorSide: "REVIEWER",
  page: null,
  x: null,
  y: null,
  body: "Please redo page 2",
  createdAt: "2026-11-01T10:00:00Z",
  resolvedAt: null,
  ...over,
});

describe("ThreadPanel authors", () => {
  it("shows when each comment and reply was written", () => {
    const replyAt = "2026-11-03T15:30:00Z";
    render(
      <ThreadPanel
        comments={[comment({}), comment({ id: "c2", parentId: "c1", createdAt: replyAt })]}
        latestRevisionNo={1}
        onReply={vi.fn()}
      />,
    );
    expect(screen.getByText(formatWhen("2026-11-01T10:00:00Z"))).toHaveAttribute("datetime", "2026-11-01T10:00:00Z");
    expect(screen.getByText(formatWhen(replyAt))).toHaveAttribute("datetime", replyAt);
  });

  it("counts the open threads in the panel header", () => {
    render(
      <ThreadPanel
        comments={[comment({}), comment({ id: "c2", resolvedAt: "2026-11-02T10:00:00Z" }), comment({ id: "c3", parentId: "c1" })]}
        latestRevisionNo={1}
        onReply={vi.fn()}
      />,
    );
    expect(screen.getByText("1 open")).toBeInTheDocument();
  });

  it("rendersDeletedUserForACommentWhoseAuthorIsNull", () => {
    render(
      <ThreadPanel
        comments={[
          comment({ authorId: null, authorName: null }),
          comment({ id: "c2", parentId: "c1", authorId: "a2", authorName: "Asha Rao", authorSide: "HEALER", body: "Done" }),
        ]}
        latestRevisionNo={1}
        onReply={vi.fn()}
      />,
    );

    expect(screen.getByText("Deleted user")).toBeInTheDocument();
    expect(screen.getByText("Please redo page 2")).toBeInTheDocument();
    expect(screen.getByText("Asha Rao")).toBeInTheDocument();
  });

  it("stillFallsBackToTheRoleWhenAKnownAuthorHasNoName", () => {
    render(<ThreadPanel comments={[comment({ authorName: "" })]} latestRevisionNo={1} onReply={vi.fn()} />);

    expect(screen.queryByText("Deleted user")).not.toBeInTheDocument();
    expect(screen.getAllByText("Instructor").length).toBeGreaterThan(0);
  });
});
