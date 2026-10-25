import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import MasterDataPage from "./MasterDataPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
vi.mock("../../hooks/useMyPermissions", () => ({
  useCan: () => () => true,
  // No active unlock of shared entries (useElevation reads this).
  useMyPermissions: () => ({ data: { elevations: {} } }),
}));
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { role: "SUPADM" } }),
}));
vi.mock("../../ui-version", () => ({
  useUiVersion: () => ({ isNewView: false }),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../../lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

const color = (id: string, name: string, category: string | null) => ({
  id,
  code: id.toUpperCase(),
  name,
  colorHex: "#ffffff",
  properties: [],
  category,
  healerId: null,
});

function renderPage() {
  mGet.mockImplementation((url: string) =>
    Promise.resolve({
      data: {
        data: url.endsWith("/colors")
          ? [
              color("green", "Green", "CLEANSING"),
              color("red", "Red", "ENERGISING"),
              // Used both ways: one row per category.
              color("violet_e", "Violet", "ENERGISING"),
              color("violet_c", "Violet", "CLEANSING"),
            ]
          : [],
      },
    })
  );
  return renderWithDataRouter([{ path: "/", element: <MasterDataPage /> }]);
}

const desktopTable = () => screen.getByRole("table");

// user.type is slow when the full suite runs in parallel; the default 5s is not enough there.
const TYPING_TIMEOUT = 15_000;

describe("MasterDataPage prana color categories", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mPost.mockResolvedValue({ data: {} });
  });

  it("lists only the selected category's colors", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("tab", { name: "Energising" });
    await waitFor(() => expect(within(desktopTable()).getByText("RED")).toBeInTheDocument());
    expect(within(desktopTable()).getByText("VIOLET_E")).toBeInTheDocument();
    expect(within(desktopTable()).queryByText("VIOLET_C")).not.toBeInTheDocument();
    expect(within(desktopTable()).queryByText("GREEN")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Cleansing" }));

    expect(within(desktopTable()).getByText("GREEN")).toBeInTheDocument();
    expect(within(desktopTable()).getByText("VIOLET_C")).toBeInTheDocument();
    expect(within(desktopTable()).queryByText("RED")).not.toBeInTheDocument();
  });

  it("a color added from a tab is saved in that tab's category", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("tab", { name: "Cleansing" }));
    await user.click(screen.getByRole("button", { name: /Add Color/ }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("radio", { name: "Cleansing" })).toHaveAttribute("aria-checked", "true");
    expect(within(dialog).getByRole("radio", { name: "Energising" })).toHaveAttribute("aria-checked", "false");

    const [name, code] = within(dialog).getAllByRole("textbox");
    await user.type(name, "Orange");
    await user.type(code, "orange_c");
    await user.click(within(dialog).getByRole("button", { name: "Add Color" }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith(
        "/api/v1/masterdata/colors",
        expect.objectContaining({ code: "ORANGE_C", category: "CLEANSING" })
      )
    );
  }, TYPING_TIMEOUT);

  it("shows the server's message when the name already exists in that category", async () => {
    mPost.mockRejectedValue({
      response: { data: { detail: '"Green" already exists in cleansing colors' } },
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("tab", { name: "Cleansing" }));
    await user.click(screen.getByRole("button", { name: /Add Color/ }));
    const dialog = await screen.findByRole("dialog");
    const [name, code] = within(dialog).getAllByRole("textbox");
    await user.type(name, "Green");
    await user.type(code, "GREEN_2");
    await user.click(within(dialog).getByRole("button", { name: "Add Color" }));

    expect(await within(dialog).findByText('"Green" already exists in cleansing colors')).toBeInTheDocument();
  }, TYPING_TIMEOUT);
});
