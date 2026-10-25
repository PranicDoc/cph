import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import PackagesPage from "./PackagesPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
// Every verb is granted unless a test narrows it with denied.add("RESOURCE:VERB").
const perms = vi.hoisted(() => ({ denied: new Set<string>() }));
vi.mock("../../hooks/useMyPermissions", () => ({
  useCan: () => (resource: string, verb: string) => !perms.denied.has(`${resource}:${verb}`),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../../lib/axios";
import { toast } from "sonner";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPut = api.put as unknown as ReturnType<typeof vi.fn>;
const mDelete = api.delete as unknown as ReturnType<typeof vi.fn>;

const pkg = (over: Record<string, unknown> = {}) => ({
  id: "p1",
  name: "PRO",
  credits: 200,
  price_paise: 149900,
  currency: "INR",
  active: true,
  display_order: 3,
  validity_value: 30,
  validity_unit: "DAYS",
  created_at: "2026-01-01T00:00:00Z",
  updated_at: "2026-01-01T00:00:00Z",
  ...over,
});

function renderPage(packages: unknown[]) {
  mGet.mockResolvedValue({ data: packages });
  return renderWithDataRouter([{ path: "/", element: <PackagesPage /> }]);
}

describe("PackagesPage enable/disable and delete", () => {
  beforeEach(() => {
  perms.denied.clear();
    vi.clearAllMocks();
    mPut.mockResolvedValue({ data: {} });
    mDelete.mockResolvedValue({ data: "" });
  });

  it("disables an active package from its switch", async () => {
    const user = userEvent.setup();
    renderPage([pkg()]);

    const [toggle] = await screen.findAllByRole("switch", { name: /Disable PRO/ });
    await user.click(toggle);

    await waitFor(() =>
      expect(mPut).toHaveBeenCalledWith("/api/admin/credit/packages/p1", { active: false })
    );
  });

  it("re-enables a disabled package", async () => {
    const user = userEvent.setup();
    renderPage([pkg({ active: false })]);

    const [toggle] = await screen.findAllByRole("switch", { name: /Enable PRO/ });
    await user.click(toggle);

    await waitFor(() =>
      expect(mPut).toHaveBeenCalledWith("/api/admin/credit/packages/p1", { active: true })
    );
  });

  it("deletes a package only after confirming, even when it is disabled", async () => {
    const user = userEvent.setup();
    renderPage([pkg({ active: false })]);

    const [trash] = await screen.findAllByRole("button", { name: /Delete PRO/ });
    await user.click(trash);
    expect(mDelete).not.toHaveBeenCalled();

    await user.click(await screen.findByRole("button", { name: /Delete permanently/ }));

    await waitFor(() => expect(mDelete).toHaveBeenCalledWith("/api/admin/credit/packages/p1"));
  });

  it("shows the server's reason when delete is refused", async () => {
    const user = userEvent.setup();
    mDelete.mockRejectedValue({
      response: { data: { detail: "Accounts are on the PRO plan. Disable the package instead of deleting it." } },
    });
    renderPage([pkg()]);

    const [trash] = await screen.findAllByRole("button", { name: /Delete PRO/ });
    await user.click(trash);
    await user.click(await screen.findByRole("button", { name: /Delete permanently/ }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        expect.stringContaining("Disable the package"),
        expect.anything()
      )
    );
  });
});

describe("PackagesPage actions follow the user's PACKAGES permissions", () => {
  beforeEach(() => {
    perms.denied.clear();
    vi.clearAllMocks();
  });

  it("shows no switch, Edit, Delete or New package to a read-only user", async () => {
    perms.denied.add("PACKAGES:CREATE").add("PACKAGES:UPDATE").add("PACKAGES:DELETE");
    renderPage([pkg()]);

    expect((await screen.findAllByText("PRO")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("switch")).toBeNull();
    expect(screen.queryByRole("button", { name: /Edit PRO/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Delete PRO/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /New/ })).toBeNull();
  });

  it("keeps the switch and Edit but hides Delete when only DELETE is missing", async () => {
    perms.denied.add("PACKAGES:DELETE");
    renderPage([pkg()]);

    expect((await screen.findAllByRole("switch", { name: /Disable PRO/ })).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /Edit PRO/ }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /Delete PRO/ })).toBeNull();
  });
});
