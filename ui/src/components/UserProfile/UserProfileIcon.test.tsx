import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { mockUseAuth, mockUseUiVersion, mockToastSuccess } = vi.hoisted(() => ({
  mockUseAuth: vi.fn(),
  mockUseUiVersion: vi.fn(),
  mockToastSuccess: vi.fn(),
}));

vi.mock("../../context/AuthContext", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("../../ui-version", () => ({ useUiVersion: () => mockUseUiVersion() }));
vi.mock("sonner", () => ({ toast: { success: mockToastSuccess, error: vi.fn() } }));
vi.mock("../PlanPicker", () => ({ PlanPicker: () => <div>plan picker</div> }));

import { UserProfileIcon } from "./UserProfileIcon";

// The first Radix menu render is slow when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

const setUiVersion = vi.fn();
const setTheme = vi.fn();

function view(overrides: Record<string, unknown> = {}) {
  mockUseUiVersion.mockReturnValue({
    uiVersion: "classic",
    isNewView: false,
    canSwitch: true,
    setUiVersion,
    theme: "dark",
    resolvedTheme: "dark",
    setTheme,
    ...overrides,
  });
}

async function openMenu() {
  const user = userEvent.setup();
  render(<UserProfileIcon />);
  await user.click(screen.getByRole("button"));
  return user;
}

describe("UserProfileIcon, view switch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      user: { id: "1", fullName: "Asha Rao", email: "asha@example.com" },
      logout: vi.fn(),
    });
  });

  it("offers 'Try the new view' in the classic view and switches when chosen", async () => {
    view();
    const user = await openMenu();
    await user.click(await screen.findByRole("menuitem", { name: /try the new view/i }));
    expect(setUiVersion).toHaveBeenCalledWith("new");
    expect(mockToastSuccess).toHaveBeenCalled();
  });

  it("does not show theme or feedback items in the classic view", async () => {
    view();
    await openMenu();
    await screen.findByRole("menuitem", { name: /try the new view/i });
    expect(screen.queryByText(/share feedback/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitemradio")).not.toBeInTheDocument();
  });

  it("offers 'Switch to classic view' in the new view and switches back when chosen", async () => {
    view({ uiVersion: "new", isNewView: true });
    const user = await openMenu();
    await user.click(await screen.findByRole("menuitem", { name: /switch to classic view/i }));
    expect(setUiVersion).toHaveBeenCalledWith("classic");
  });

  it("links feedback to the HealingARK mailbox in the new view", async () => {
    view({ uiVersion: "new", isNewView: true });
    await openMenu();
    const link = await screen.findByRole("menuitem", { name: /share feedback/i });
    expect(link).toHaveAttribute("href", expect.stringContaining("mailto:hello@healingark.in"));
  });

  it("lets the user pick Dark, Light or System in the new view", async () => {
    view({ uiVersion: "new", isNewView: true, theme: "dark" });
    const user = await openMenu();
    expect(await screen.findByRole("menuitemradio", { name: "Dark" })).toBeChecked();
    await user.click(screen.getByRole("menuitemradio", { name: "Light" }));
    expect(setTheme).toHaveBeenCalledWith("light");
  });

  it("hides every view item when the new view is switched off for this build", async () => {
    view({ canSwitch: false });
    await openMenu();
    await screen.findByRole("menuitem", { name: /upgrade plan/i });
    expect(screen.queryByRole("menuitem", { name: /new view/i })).not.toBeInTheDocument();
  });
});
