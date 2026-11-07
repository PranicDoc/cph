import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient } from "@tanstack/react-query";

import CloseCase from "./CloseCase";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("../../ui-version", () => ({
  useUiVersion: () => ({ isNewView: true }),
}));
const ref = vi.hoisted(() => ({ loading: false }));
vi.mock("@/hooks/useReferenceData", () => ({
  useReferenceData: () => ({
    colors: ref.loading
      ? []
      : [
          { id: "g", code: "G", name: "Green", properties: ["Cooling", "Soothing"], category: "CLEANSING" },
          { id: "b", code: "B", name: "Blue", properties: ["Calming"], category: "CLEANSING" },
          { id: "y", code: "Y", name: "Yellow", properties: [], category: "ENERGISING" },
        ],
    loading: ref.loading,
    error: null,
  }),
}));
vi.mock("@/hooks/admin/useMasterData", () => {
  // One array, as react-query hands back the same data on every render.
  const data = [{ id: "pr1", name: "General sweeping" }];
  return { useProtocols: () => ({ data, isLoading: false }) };
});
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "@/lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

const patient = { id: "p1", firstName: "Asha", lastName: "Rao", healingCaseId: "c1" };
const solarPlexus = {
  chakra: "Solar Plexus",
  energisingColors: [],
  cleansingColors: [{ color: "Green", properties: [] }],
  reason: "Anger held here",
};

function renderPage({
  selections = [solarPlexus] as unknown[],
  closure = undefined as unknown,
  chakras = ["Solar Plexus", "Spleen"],
  caseGet = undefined as undefined | (() => Promise<unknown>),
  usual = { overall: { cleansing: [], energising: [] }, byChakra: {} } as unknown,
} = {}) {
  mGet.mockImplementation((url: string) => {
    if (url === "/api/v1/cases/c1/protocol-understanding/usual-picks") {
      return usual === "error" ? Promise.reject(new Error("boom")) : Promise.resolve({ data: usual });
    }
    if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: chakras } });
    if (url.endsWith("/protocol-understanding")) return Promise.resolve({ data: { chakraSelections: selections } });
    if (url === "/api/v1/cases/c1") return caseGet ? caseGet() : Promise.resolve({ data: closure ? { closure } : {} });
    return Promise.resolve({ data: {} });
  });
  mPost.mockResolvedValue({ data: {} });
  return renderWithDataRouter(
    [
      { path: "/session/c1", element: <p>Session page</p> },
      { path: "/cases/:healingCaseId/close", element: <CloseCase /> },
    ],
    { initialEntries: ["/session/c1", { pathname: "/cases/c1/close", state: { patient } } as never] }
  );
}

const list = () => screen.getByRole("navigation", { name: "Close case steps" });
const closeButtons = () => screen.getAllByRole("button", { name: "Close case" });
const panelTitle = () => screen.getByRole("heading", { level: 2 });

describe("CloseCase page", () => {
  const stubMatchMedia = window.matchMedia;
  beforeEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
    ref.loading = false;
    window.matchMedia = stubMatchMedia;
  });

  it("lists every scanned chakra with its status and reason, the progress, and the wrap-up step", async () => {
    renderPage();

    const solar = await screen.findByRole("button", { name: /Solar Plexus/ });
    expect(solar).toHaveTextContent("Anger held here");
    expect(solar).toHaveTextContent("Now"); // the selected one
    const spleen = within(list()).getByRole("button", { name: /Spleen/ });
    expect(spleen).toHaveTextContent("Needs a reason");
    expect(spleen).toHaveTextContent("To do");
    expect(within(list()).getByRole("button", { name: /Wrap up/ })).toHaveTextContent("Protocols, comments, continued?");
    expect(within(list()).getByRole("button", { name: /Wrap up/ })).toHaveTextContent("Last");
    expect(screen.getByText("1 of 2 done")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "1");
    expect(screen.getByRole("heading", { level: 1, name: "Close case for Asha Rao" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back to session" })).toBeInTheDocument();

    await userEvent.setup().click(spleen);
    expect(within(list()).getByRole("button", { name: /Solar Plexus/ })).toHaveTextContent("Done");
  });

  it("shows only the selected chakra, its required reason above the colours, with a 300 character count", async () => {
    renderPage();

    const reason = await screen.findByLabelText(/Why did you work on this chakra/);
    expect(panelTitle()).toHaveTextContent("Solar Plexus");
    expect(within(list()).getByRole("button", { name: /Solar Plexus/ })).toHaveAttribute("aria-current", "step");
    expect(reason).toHaveValue("Anger held here");
    expect(reason).toBeRequired();
    expect(reason).toHaveAttribute("maxLength", "300");
    expect(screen.getByText("15/300")).toBeInTheDocument();
    expect(panelTitle().parentElement).toHaveTextContent("Solar Plexuschakra 1 of 2");
    const cleanse = screen.getByRole("region", { name: "1. Cleanse" });
    expect(reason.compareDocumentPosition(cleanse) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(cleanse).getByText("1 colour")).toBeInTheDocument();
    expect(within(cleanse).getByRole("list", { name: "Cleansing colours added" })).toHaveTextContent("GreenNo property");
    expect(within(cleanse).getByRole("button", { name: "Add or change cleansing colours" })).toBeInTheDocument();
    const energise = screen.getByRole("region", { name: "2. Energise" });
    expect(within(energise).getByText("No colours yet")).toBeInTheDocument();
    expect(within(energise).getByRole("button", { name: "Add energising colours" })).toBeInTheDocument();
  });

  it("keeps Close case off until every chakra has a reason", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(/Why did you work on this chakra/);
    closeButtons().forEach((b) => expect(b).toBeDisabled());

    await user.click(within(list()).getByRole("button", { name: /Spleen/ }));
    await user.type(screen.getByLabelText(/Why did you work on this chakra/), "   ");
    closeButtons().forEach((b) => expect(b).toBeDisabled());

    await user.type(screen.getByLabelText(/Why did you work on this chakra/), "Sluggish");
    await waitFor(() => closeButtons().forEach((b) => expect(b).toBeEnabled()));
    expect(screen.getByText("2 of 2 done")).toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();
  });

  it("Save and next saves only the chakras with a reason and moves to the next one", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(/Why did you work on this chakra/);
    await user.click(screen.getByRole("button", { name: "Save and next: Spleen" }));

    expect(mPost).toHaveBeenCalledTimes(1);
    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/protocol-understanding", { chakraSelections: [solarPlexus] });
    await waitFor(() => expect(panelTitle()).toHaveTextContent("Spleen"));
    expect(screen.getByRole("button", { name: "Solar Plexus" })).toBeInTheDocument(); // the "back" button
  });

  it("after the last chakra goes to Wrap up", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await within(await screen.findByRole("navigation", { name: "Close case steps" })).findByRole("button", { name: /Spleen/ }));
    await user.type(screen.getByLabelText(/Why did you work on this chakra/), "Sluggish");
    await user.click(screen.getByRole("button", { name: "Save and next: Wrap up" }));

    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/protocol-understanding", {
      chakraSelections: [solarPlexus, { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: "Sluggish" }],
    });
    await waitFor(() => expect(panelTitle()).toHaveTextContent("Wrap up"));
    expect(screen.getByText("Recommended protocols for the patient")).toBeInTheDocument();
    expect(screen.getByLabelText("Comments")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Healing will continue" })).toHaveAttribute("aria-checked", "false");
    expect(screen.getByText("On: the case stays open as continued. Off: the patient gets the final feedback request.")).toBeInTheDocument();
    expect(screen.getByText("2 of 2 chakras done")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Spleen" })).toBeInTheDocument();
  });

  it("selecting an item in the list opens it without saving", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(/Why did you work on this chakra/);
    await user.click(within(list()).getByRole("button", { name: /Wrap up/ }));
    expect(panelTitle()).toHaveTextContent("Wrap up");
    expect(mPost).not.toHaveBeenCalled();
  });

  it("Close case saves the understanding, then closes with the same body as before, refreshes patients and goes back", async () => {
    const user = userEvent.setup();
    const invalidate = vi.spyOn(QueryClient.prototype, "invalidateQueries");
    const { router } = renderPage();

    await user.click(await within(await screen.findByRole("navigation", { name: "Close case steps" })).findByRole("button", { name: /Spleen/ }));
    await user.type(screen.getByLabelText(/Why did you work on this chakra/), "Sluggish");
    await user.click(within(list()).getByRole("button", { name: /Wrap up/ }));
    await user.type(screen.getByLabelText("Comments"), "Gentle progress");
    await user.click(screen.getByRole("switch", { name: "Healing will continue" }));
    mPost.mockClear();
    await user.click(closeButtons()[1]);

    await waitFor(() => expect(router.state.location.pathname).toBe("/session/c1"));
    expect(mPost.mock.calls).toEqual([
      [
        "/api/v1/cases/c1/protocol-understanding",
        {
          chakraSelections: [solarPlexus, { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: "Sluggish" }],
        },
      ],
      ["/api/v1/cases/c1/close", { additionalComments: "Gentle progress", isHealingContinued: true }],
    ]);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["patients"] });
  });

  it("keeps one property per colour in the colour picker: picking a second replaces the first", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Add or change cleansing colours" }));
    const sheet = screen.getByRole("dialog", { name: "Cleansing colours" });
    expect(sheet).toHaveTextContent("Solar Plexus, 1 added");
    await user.click(within(sheet).getByRole("button", { name: /^Green/ }));
    const props = within(sheet).getByRole("group", { name: "Property for Green" });
    expect(props).toHaveTextContent("Property (pick one)");
    await user.click(within(props).getByRole("button", { name: "Cooling" }));
    await user.click(within(props).getByRole("button", { name: "Soothing" }));
    expect(within(props).getByRole("button", { name: "Cooling" })).toHaveAttribute("aria-pressed", "false");
    expect(within(props).getByRole("button", { name: "Soothing" })).toHaveAttribute("aria-pressed", "true");
    await user.click(within(sheet).getByRole("button", { name: "Done" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByRole("list", { name: "Cleansing colours added" })).toHaveTextContent("GreenSoothing");
    await user.click(screen.getByRole("button", { name: "Save and next: Spleen" }));

    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/protocol-understanding", {
      chakraSelections: [{ ...solarPlexus, cleansingColors: [{ color: "Green", properties: ["Soothing"] }] }],
    });
  });

  it("colour picker: tapping a colour adds and expands it, one open at a time; search, remove, and tapping the picked property clears it", async () => {
    const user = userEvent.setup();
    renderPage();

    const opener = await screen.findByRole("button", { name: "Add or change cleansing colours" });
    await user.click(opener);
    const sheet = screen.getByRole("dialog", { name: "Cleansing colours" });
    expect(within(sheet).getByText("Tap a colour to add it")).toBeInTheDocument();
    const green = within(sheet).getByRole("button", { name: /^Green/ });
    expect(green).toHaveTextContent("Added");
    expect(green).toHaveAttribute("aria-expanded", "false");

    const blue = within(sheet).getByRole("button", { name: /^Blue/ });
    expect(blue).not.toHaveTextContent("Added");
    await user.click(blue);
    expect(blue).toHaveTextContent("Added");
    expect(blue).toHaveAttribute("aria-expanded", "true");
    expect(sheet).toHaveTextContent("Solar Plexus, 2 added");
    await user.click(green);
    expect(green).toHaveAttribute("aria-expanded", "true");
    expect(blue).toHaveAttribute("aria-expanded", "false");

    const cooling = within(sheet).getByRole("button", { name: "Cooling" });
    await user.click(cooling);
    expect(cooling).toHaveAttribute("aria-pressed", "true");
    await user.click(cooling);
    expect(cooling).toHaveAttribute("aria-pressed", "false");

    await user.click(within(sheet).getByRole("button", { name: "Remove Blue" }));
    expect(within(sheet).getByRole("button", { name: /^Blue/ })).not.toHaveTextContent("Added");
    expect(within(sheet).queryByRole("button", { name: "Remove Blue" })).not.toBeInTheDocument();

    await user.type(within(sheet).getByLabelText("Search cleansing colours"), "gre");
    expect(within(sheet).queryByRole("button", { name: /^Blue/ })).not.toBeInTheDocument();
    expect(within(sheet).getByRole("button", { name: /^Green/ })).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(opener).toHaveFocus();
  });

  it("shows the healer's usual picks for this chakra, else overall, and a chip adds that colour with its property", async () => {
    const user = userEvent.setup();
    renderPage({
      usual: {
        overall: { cleansing: [{ color: "Blue", property: null }], energising: [] },
        byChakra: { "Solar Plexus": { cleansing: [{ color: "Green", property: "Cooling" }], energising: [] } },
      },
    });

    const cleanse = await screen.findByRole("region", { name: "1. Cleanse" });
    const picks = await within(cleanse).findByRole("group", { name: "Your usual picks" });
    const chip = within(picks).getByRole("button", { name: "Green, Cooling" });
    expect(chip).toHaveAttribute("aria-pressed", "false");
    await user.click(chip);
    expect(chip).toHaveAttribute("aria-pressed", "true");
    expect(within(cleanse).getByRole("list", { name: "Cleansing colours added" })).toHaveTextContent("GreenCooling");
    expect(within(screen.getByRole("region", { name: "2. Energise" })).queryByText("Your usual picks")).not.toBeInTheDocument();

    await user.click(within(list()).getByRole("button", { name: /Spleen/ }));
    const spleenPicks = await within(screen.getByRole("region", { name: "1. Cleanse" })).findByRole("group", { name: "Your usual picks" });
    expect(within(spleenPicks).getByRole("button", { name: "Blue" })).toBeInTheDocument();
  });

  it("hides the usual picks when they fail to load", async () => {
    renderPage({ usual: "error" });

    const cleanse = await screen.findByRole("region", { name: "1. Cleanse" });
    await waitFor(() => expect(mGet).toHaveBeenCalledWith("/api/v1/cases/c1/protocol-understanding/usual-picks"));
    expect(within(cleanse).queryByText("Your usual picks")).not.toBeInTheDocument();
  });

  it("editing a closed case loads its closure into Wrap up, and the header Close case closes it again", async () => {
    const user = userEvent.setup();
    const { router } = renderPage({
      selections: [solarPlexus, { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: "Sluggish" }],
      closure: {
        additionalComments: "The patient is advised to follow: \nGeneral sweeping\n\nKeep calm",
        isHealingContinued: true,
      },
    });

    await screen.findByText("2 of 2 done");
    await user.click(within(list()).getByRole("button", { name: /Wrap up/ }));
    await waitFor(() => expect(screen.getByLabelText("Comments")).toHaveValue("Keep calm"));
    expect(screen.getByRole("switch", { name: "Healing will continue" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("General sweeping")).toBeInTheDocument();

    await user.click(closeButtons()[0]);
    await waitFor(() => expect(router.state.location.pathname).toBe("/session/c1"));
    expect(mPost).toHaveBeenLastCalledWith("/api/v1/cases/c1/close", {
      additionalComments: "The patient is advised to follow: \nGeneral sweeping\n\nKeep calm",
      isHealingContinued: true,
    });
  });

  it("does not save when a chakra saved before now lacks a reason, so its saved colours are not erased", async () => {
    const user = userEvent.setup();
    renderPage({
      selections: [solarPlexus, { chakra: "Spleen", energisingColors: [], cleansingColors: [{ color: "Green", properties: [] }] }],
    });

    await screen.findByLabelText(/Why did you work on this chakra/);
    await user.click(screen.getByRole("button", { name: "Save and next: Spleen" }));

    await waitFor(() => expect(panelTitle()).toHaveTextContent("Spleen"));
    expect(mPost).not.toHaveBeenCalled();
    expect(screen.queryByText(/Add a reason to/)).not.toBeInTheDocument();
  });

  it("does not save when the healer clears a saved reason", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.clear(await screen.findByLabelText(/Why did you work on this chakra/));
    await user.click(screen.getByRole("button", { name: "Save and next: Spleen" }));

    await waitFor(() => expect(panelTitle()).toHaveTextContent("Spleen"));
    expect(mPost).not.toHaveBeenCalled();
    expect(screen.queryByText(/Add a reason to/)).not.toBeInTheDocument();
  });

  it("keeps Close case off until the existing closure has loaded", async () => {
    renderPage({
      selections: [solarPlexus, { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: "Sluggish" }],
      caseGet: () => new Promise(() => {}),
    });

    await screen.findByText("2 of 2 done");
    closeButtons().forEach((b) => expect(b).toBeDisabled());
  });

  it("keeps Close case off with a message when the existing closure fails to load", async () => {
    renderPage({
      selections: [solarPlexus, { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: "Sluggish" }],
      caseGet: () => Promise.reject(new Error("boom")),
    });

    expect(await screen.findByText(/Couldn't load this case's closure details/)).toBeInTheDocument();
    closeButtons().forEach((b) => expect(b).toBeDisabled());
  });

  it("disables the list while a save is running", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText(/Why did you work on this chakra/);
    mPost.mockReturnValue(new Promise(() => {}));

    await user.click(screen.getByRole("button", { name: "Save and next: Spleen" }));
    within(list()).getAllByRole("button").forEach((b) => expect(b).toBeDisabled());
  });

  it("moves focus to the opened item's heading, and Back to list returns it to that row", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(/Why did you work on this chakra/);
    await user.click(screen.getByRole("button", { name: "Save and next: Spleen" }));
    await waitFor(() => expect(panelTitle()).toHaveFocus());
    expect(panelTitle()).toHaveTextContent("Spleen");

    await user.click(screen.getByRole("button", { name: "All chakras" }));
    await waitFor(() => expect(within(list()).getByRole("button", { name: /Spleen/ })).toHaveFocus());
  });

  it("names the recommended-protocols picker, without a saving hint", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(/Why did you work on this chakra/);
    expect(screen.queryByText(/saved once it has a reason/)).not.toBeInTheDocument();
    await user.click(within(list()).getByRole("button", { name: /Wrap up/ }));
    expect(screen.getByRole("group", { name: "Recommended protocols for the patient" })).toBeInTheDocument();
  });

  it("closes a case with no scanned chakras without saving an empty understanding", async () => {
    const user = userEvent.setup();
    const { router } = renderPage({ chakras: [], selections: [] });

    await waitFor(() => expect(panelTitle()).toHaveTextContent("Wrap up"));
    await waitFor(() => expect(closeButtons()[0]).toBeEnabled());
    await user.click(closeButtons()[0]);

    await waitFor(() => expect(router.state.location.pathname).toBe("/session/c1"));
    expect(mPost.mock.calls).toEqual([["/api/v1/cases/c1/close", { additionalComments: "", isHealingContinued: false }]]);
  });

  it("tops a chakra's usual picks up from overall, without duplicates or colours outside the catalog", async () => {
    renderPage({
      usual: {
        overall: {
          cleansing: [
            { color: "Green", property: "Cooling" },
            { color: "Pink", property: "Soothing" },
            { color: "Blue", property: null },
          ],
          energising: [],
        },
        byChakra: { "Solar Plexus": { cleansing: [{ color: "Green", property: "Cooling" }], energising: [] } },
      },
    });

    const cleanse = await screen.findByRole("region", { name: "1. Cleanse" });
    const picks = await within(cleanse).findByRole("group", { name: "Your usual picks" });
    expect(within(picks).getAllByRole("button").map((b) => b.textContent)).toEqual(["Green, Cooling", "Blue"]);
  });

  it("says the colours are loading in the picker while the catalog loads", async () => {
    ref.loading = true;
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Add or change cleansing colours" }));
    const sheet = screen.getByRole("dialog", { name: "Cleansing colours" });
    expect(within(sheet).getByText("Loading colours…")).toBeInTheDocument();
    expect(within(sheet).queryByText("No colours found.")).not.toBeInTheDocument();
  });

  it("opens the colour picker inside the chakra panel on desktop, and over the page on a phone", async () => {
    const user = userEvent.setup();
    window.matchMedia = ((query: string) => ({ ...stubMatchMedia(query), matches: query === "(min-width: 768px)" })) as typeof window.matchMedia;
    const { unmount } = renderPage();

    await user.click(await screen.findByRole("button", { name: "Add or change cleansing colours" }));
    const panel = document.querySelector(".hl-cc-panel");
    expect(panel?.contains(screen.getByRole("dialog", { name: "Cleansing colours" }))).toBe(true);
    await user.keyboard("{Escape}");
    unmount();

    window.matchMedia = stubMatchMedia;
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Add or change cleansing colours" }));
    expect(document.querySelector(".hl-cc-panel")?.contains(screen.getByRole("dialog", { name: "Cleansing colours" }))).toBe(false);
  });
});
