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

const color = (name: string, categories: string[]) => ({
  id: name,
  code: name.toUpperCase(),
  name,
  colorHex: "#ffffff",
  properties: [],
  categories,
  healerId: null,
});

function renderPage() {
  mGet.mockImplementation((url: string) =>
    Promise.resolve({
      data: {
        data: url.endsWith("/colors")
          ? [color("Green", ["CLEANSING"]), color("Red", ["ENERGISING"]), color("Violet", ["CLEANSING", "ENERGISING"])]
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

  it("lists only the selected category's colors, showing a two-category color in both tabs", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("tab", { name: "Energising" });
    await waitFor(() => expect(within(desktopTable()).getByText("Red")).toBeInTheDocument());
    expect(within(desktopTable()).getByText("Violet")).toBeInTheDocument();
    expect(within(desktopTable()).queryByText("Green")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Cleansing" }));

    expect(within(desktopTable()).getByText("Green")).toBeInTheDocument();
    expect(within(desktopTable()).getByText("Violet")).toBeInTheDocument();
    expect(within(desktopTable()).queryByText("Red")).not.toBeInTheDocument();
  });

  it("a color added from a tab starts in that tab's category", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("tab", { name: "Cleansing" }));
    await user.click(screen.getByRole("button", { name: /Add Color/ }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("checkbox", { name: "Cleansing" })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: "Energising" })).not.toBeChecked();

    const [name, code] = within(dialog).getAllByRole("textbox");
    await user.type(name, "Orange");
    await user.type(code, "orange");
    await user.click(within(dialog).getByRole("checkbox", { name: "Energising" }));
    await user.click(within(dialog).getByRole("button", { name: "Add Color" }));

    await waitFor(() =>
      expect(mPost).toHaveBeenCalledWith(
        "/api/v1/masterdata/colors",
        expect.objectContaining({ code: "ORANGE", categories: ["CLEANSING", "ENERGISING"] })
      )
    );
  }, TYPING_TIMEOUT);

  it("refuses to save a color with no category", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /Add Color/ }));
    const dialog = await screen.findByRole("dialog");
    const [name, code] = within(dialog).getAllByRole("textbox");
    await user.type(name, "Orange");
    await user.type(code, "ORANGE");
    await user.click(within(dialog).getByRole("checkbox", { name: "Energising" }));
    await user.click(within(dialog).getByRole("button", { name: "Add Color" }));

    expect(await within(dialog).findByText("Select at least one category.")).toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();
  }, TYPING_TIMEOUT);
});
