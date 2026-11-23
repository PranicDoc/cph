import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

const { mockUseMyPermissions } = vi.hoisted(() => ({ mockUseMyPermissions: vi.fn() }));
vi.mock("../hooks/useMyPermissions", () => ({ useMyPermissions: () => mockUseMyPermissions() }));

import { CaseReviewRoute } from "./CaseReviewRoute";

function at(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<p>home</p>} />
        <Route element={<CaseReviewRoute />}>
          <Route path="/reviews" element={<p>review queue</p>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe("CaseReviewRoute", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders nothing while the permissions load, so a deep link neither flashes nor bounces", () => {
    mockUseMyPermissions.mockReturnValue({ data: undefined, isPending: true });
    const { container } = at("/reviews");
    expect(container).toBeEmptyDOMElement();
  });

  it("sends the user home when case review is off", () => {
    mockUseMyPermissions.mockReturnValue({ data: { grants: {}, caseReviewEnabled: false }, isPending: false });
    at("/reviews");
    expect(screen.getByText("home")).toBeInTheDocument();
    expect(screen.queryByText("review queue")).not.toBeInTheDocument();
  });

  it("sends the user home when the permissions request failed", () => {
    mockUseMyPermissions.mockReturnValue({ data: undefined, isPending: false, isError: true });
    at("/reviews");
    expect(screen.getByText("home")).toBeInTheDocument();
  });

  it("renders the review screen when case review is on", () => {
    mockUseMyPermissions.mockReturnValue({ data: { grants: {}, caseReviewEnabled: true }, isPending: false });
    at("/reviews");
    expect(screen.getByText("review queue")).toBeInTheDocument();
  });
});
