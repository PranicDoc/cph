import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const { mockUseUiVersion } = vi.hoisted(() => ({ mockUseUiVersion: vi.fn() }));
vi.mock("./UiVersionContext", () => ({ useUiVersion: () => mockUseUiVersion() }));

import { versioned } from "./versioned";

function Classic({ name }: { name: string }) {
  return <div>classic page for {name}</div>;
}
function NewView({ name }: { name: string }) {
  return <div>new page for {name}</div>;
}

describe("versioned", () => {
  it("renders the classic page in the classic view and never loads the new one", () => {
    mockUseUiVersion.mockReturnValue({ isNewView: false });
    const loadNew = vi.fn(() => Promise.resolve({ default: NewView }));
    const Page = versioned(Classic, loadNew);
    render(<Page name="Sunita" />);
    expect(screen.getByText("classic page for Sunita")).toBeInTheDocument();
    expect(loadNew).not.toHaveBeenCalled();
  });

  it("renders the new page, with the same props, in the new view", async () => {
    mockUseUiVersion.mockReturnValue({ isNewView: true });
    const Page = versioned(Classic, () => Promise.resolve({ default: NewView }));
    render(<Page name="Sunita" />);
    expect(await screen.findByText("new page for Sunita")).toBeInTheDocument();
    expect(screen.queryByText(/classic page/)).not.toBeInTheDocument();
  });
});
