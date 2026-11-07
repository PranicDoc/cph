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
vi.mock("@/hooks/useReferenceData", () => ({
  useReferenceData: () => ({
    colors: [{ id: "g", code: "G", name: "Green", properties: [], category: "CLEANSING" }],
    loading: false,
    error: null,
  }),
}));
vi.mock("@/hooks/admin/useMasterData", () => ({
  useProtocols: () => ({ data: [], isLoading: false }),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "@/lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

const patient = { id: "p1", firstName: "Asha", lastName: "Rao", healingCaseId: "c1" };

function renderPage() {
  mGet.mockImplementation((url: string) => {
    if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: ["Solar Plexus", "Spleen"] } });
    if (url.endsWith("/protocol-understanding")) {
      return Promise.resolve({
        data: {
          chakraSelections: [
            {
              chakra: "Solar Plexus",
              energisingColors: [],
              cleansingColors: [{ color: "Green", properties: [] }],
              reason: "Anger held here",
            },
          ],
        },
      });
    }
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

/** Opens the Spleen row (rows start collapsed but the first) and types its reason. */
async function fillSpleenReason(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(await screen.findByRole("button", { name: /Spleen/ }));
  await user.type(await screen.findByLabelText("Why this chakra?"), text);
}

describe("CloseCase page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("lists the scanned chakras with their saved colours and reason", async () => {
    renderPage();

    expect(await screen.findByRole("button", { name: /Spleen/ })).toBeInTheDocument();
    const cleansing = await screen.findByRole("group", { name: "Cleansing colors" });
    expect(within(cleansing).getByRole("button", { name: /Green/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Why this chakra?")).toHaveValue("Anger held here");
  });

  it("needs a reason for every chakra before it goes on, and shows which are missing", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("button", { name: /Spleen.*Reason needed/ })).toBeInTheDocument();
    expect(screen.getByText("Add why you worked on each chakra to continue.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();

    await fillSpleenReason(user, "   ");
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();

    await user.type(screen.getByLabelText("Why this chakra?"), "Sluggish");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeEnabled());
    expect(screen.queryByText("Add why you worked on each chakra to continue.")).not.toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();
  });

  it("asks for the reason above the colours", async () => {
    renderPage();

    const reason = await screen.findByLabelText("Why this chakra?");
    const cleansing = screen.getByRole("group", { name: "Cleansing colors" });
    expect(reason.compareDocumentPosition(cleansing) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(reason).toBeRequired();
  });

  it("limits the reason to 300 characters and shows the count", async () => {
    renderPage();

    const reason = await screen.findByLabelText("Why this chakra?");
    expect(reason).toHaveAttribute("maxLength", "300");
    expect(screen.getByText("15/300")).toBeInTheDocument();
  });

  it("saves the reasons, then closes the case without the understanding step and goes back", async () => {
    const user = userEvent.setup();
    const invalidate = vi.spyOn(QueryClient.prototype, "invalidateQueries");
    const { router } = renderPage();

    const reason = await screen.findByLabelText("Why this chakra?");
    await user.clear(reason);
    await user.type(reason, "Fear");
    await fillSpleenReason(user, "Sluggish");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/protocol-understanding", {
      chakraSelections: [
        {
          chakra: "Solar Plexus",
          energisingColors: [],
          cleansingColors: [{ color: "Green", properties: [] }],
          reason: "Fear",
        },
        { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: "Sluggish" },
      ],
    });

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Is healing continued?")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Back" })).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Close Case" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/session/c1"));
    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/close", expect.anything());
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["patients"] });
  });

  it("keeps the page open when the closure dialog is cancelled", async () => {
    const user = userEvent.setup();
    const { router } = renderPage();

    await fillSpleenReason(user, "Sluggish");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Save" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/cases/c1/close");
  });
});
