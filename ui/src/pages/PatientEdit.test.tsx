import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import PatientEdit from "./PatientEdit";
import { renderWithDataRouter } from "../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({ default: { get: vi.fn(), put: vi.fn() } }));
vi.mock("../lib/axios", () => ({ default: { get: vi.fn(), put: vi.fn() } }));
const view = vi.hoisted(() => ({ isNewView: false }));
vi.mock("@/ui-version", () => ({ useUiVersion: () => view }));
vi.mock("../ui-version", () => ({ useUiVersion: () => view }));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../lib/axios";
import { toast } from "sonner";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPut = api.put as unknown as ReturnType<typeof vi.fn>;

const patient = {
  id: "p-1",
  firstName: "Asha",
  lastName: "Rao",
  dob: "1990-04-02",
  gender: "F",
  maritalStatus: "MARRIED",
  email: "asha@example.com",
  contact: "+911111111111",
  occupation: "Teacher",
  addressLine1: "12 Lake Road",
  addressLine2: null,
  city: "Pune",
  state: "MH",
  zip: "411001",
};

const history = {
  generalHealth: "GOOD",
  diet: "MIXED",
  exercise: "YOGA,WALKING",
  exerciseFrequency: "WEEKLY",
  hypertension: true,
  giIssues: false,
  stressDepressionAnxiety: false,
  pregnant: false,
  cancer: false,
  cancerLocation: null,
  cancerDetails: null,
  additionalNotes: "Knee pain",
};

type DeclarationReply = { declarationDate: string | null; exists: boolean; editable: boolean } | "fails";

function renderPage(
  historyReply: "ok" | "none" = "ok",
  declarationReply: DeclarationReply = { declarationDate: "2026-08-15", exists: true, editable: true }
) {
  mGet.mockImplementation((url: string) => {
    if (url.endsWith("/declaration-date")) {
      return declarationReply === "fails"
        ? Promise.reject({ response: { status: 500 } })
        : Promise.resolve({ data: declarationReply });
    }
    return url.endsWith("/medical-history")
      ? historyReply === "ok"
        ? Promise.resolve({ data: history })
        : Promise.reject({ response: { status: 404 } })
      : Promise.resolve({ data: patient });
  });
  return renderWithDataRouter(
    [
      { path: "/patients/:id/edit", element: <PatientEdit /> },
      { path: "/patients", element: <div>patients-page</div> },
    ],
    { initialEntries: ["/patients/p-1/edit"] }
  );
}

describe("PatientEdit page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    view.isNewView = false;
  });

  it("shows only the medical history, not the patient's personal details, and saves just the history", async () => {
    mPut.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByDisplayValue("Knee pain")).toBeInTheDocument();
    expect(mGet).toHaveBeenCalledWith("/api/v1/patients/p-1");
    // personal details are not shown or editable here
    expect(screen.queryByDisplayValue("Asha")).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue("Pune")).not.toBeInTheDocument();
    expect(screen.queryByText("Personal details")).not.toBeInTheDocument();
    expect(screen.queryByText(/Asha Rao/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(mPut).toHaveBeenCalledTimes(1));
    // exercise stored as one string; the patient record itself is never written
    expect(mPut).toHaveBeenCalledWith(
      "/api/v1/patients/p-1/medical-history",
      expect.objectContaining({
        generalHealth: "GOOD",
        diet: "MIXED",
        exercise: "YOGA,WALKING",
        exerciseFrequency: "WEEKLY",
        hypertension: true,
        cancer: false,
        cancerLocation: null,
        additionalNotes: "Knee pain",
      })
    );
    expect(mPut).not.toHaveBeenCalledWith("/api/v1/patients/p-1", expect.anything());
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(await screen.findByText("patients-page")).toBeInTheDocument();
  });

  it("stays on the page and shows the server's reason when the save is refused", async () => {
    mPut.mockRejectedValue({ response: { data: { detail: "Medical history is invalid" } } });
    const user = userEvent.setup();
    renderPage();

    await screen.findByDisplayValue("Knee pain");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Medical history is invalid", expect.anything()));
    expect(screen.queryByText("patients-page")).not.toBeInTheDocument();
  });

  it("Cancel goes back to the patients list without saving", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByDisplayValue("Knee pain");
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(await screen.findByText("patients-page")).toBeInTheDocument();
    expect(mPut).not.toHaveBeenCalled();
  });

  it("shows the medical history section pre-filled, with the declaration date", async () => {
    renderPage();

    await screen.findByDisplayValue("Knee pain");
    expect(screen.getByText("Medical history")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Knee pain")).toBeInTheDocument();
    expect(screen.getByText(/Declaration date/)).toBeInTheDocument();
    expect(screen.getByDisplayValue("15/08/2026")).toBeEnabled();
  });

  it("saves a changed declaration date after the history", async () => {
    mPut.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPage();

    const date = await screen.findByDisplayValue("15/08/2026");
    await user.clear(date);
    await user.type(date, "01082026");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(mPut).toHaveBeenCalledTimes(2));
    expect(mPut.mock.calls[0][0]).toBe("/api/v1/patients/p-1/medical-history");
    expect(mPut.mock.calls[0][1]).not.toHaveProperty("declarationDate");
    expect(mPut.mock.calls[1]).toEqual(["/api/v1/patients/p-1/declaration-date", { declarationDate: "2026-08-01" }]);
    expect(await screen.findByText("patients-page")).toBeInTheDocument();
  });

  it("a cleared date blocks the save instead of being silently ignored", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.clear(await screen.findByDisplayValue("15/08/2026"));
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled());
    expect(mPut).not.toHaveBeenCalled();
  });

  it("stays on the page and shows the reason when the date is refused", async () => {
    mPut.mockImplementation((url: string) =>
      url.endsWith("/declaration-date")
        ? Promise.reject({ response: { data: { detail: "The declaration date cannot be in the future" } } })
        : Promise.resolve({ data: {} })
    );
    const user = userEvent.setup();
    renderPage();

    const date = await screen.findByDisplayValue("15/08/2026");
    await user.clear(date);
    await user.type(date, "01082026");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith("The declaration date cannot be in the future", expect.anything())
    );
    expect(screen.queryByText("patients-page")).not.toBeInTheDocument();
  });

  it("a locked case shows the date read-only and never sends it", async () => {
    mPut.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPage("ok", { declarationDate: "2026-08-15", exists: true, editable: false });

    expect(await screen.findByDisplayValue("15/08/2026")).toBeDisabled();
    expect(screen.getByText(/read-only until the case is unlocked/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(mPut).toHaveBeenCalledTimes(1));
    expect(mPut).toHaveBeenCalledWith("/api/v1/patients/p-1/medical-history", expect.anything());
  });

  it.each([
    ["has no declaration", { declarationDate: null, exists: false, editable: false }],
    ["can't be read", "fails"],
  ] as [string, DeclarationReply][])("leaves the date off when it %s, and the history stays editable", async (_, reply) => {
    renderPage("ok", reply);

    await screen.findByDisplayValue("Knee pain");
    expect(screen.queryByText(/Declaration date/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled();
  });

  it("a patient with no saved history gets a blank medical history section", async () => {
    renderPage("none");

    await screen.findByText("Medical history");
    expect(screen.getByText("Medical history")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("Knee pain")).not.toBeInTheDocument();
  });
});
