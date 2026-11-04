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

function renderPage(historyReply: "ok" | "none" = "ok") {
  mGet.mockImplementation((url: string) =>
    url.endsWith("/medical-history")
      ? historyReply === "ok"
        ? Promise.resolve({ data: history })
        : Promise.reject({ response: { status: 404 } })
      : Promise.resolve({ data: patient })
  );
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
    // personal details are not editable here: no inputs for them, only the name as context
    expect(screen.queryByDisplayValue("Asha")).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue("Pune")).not.toBeInTheDocument();
    expect(screen.queryByText("Personal details")).not.toBeInTheDocument();
    expect(screen.getByText(/Asha Rao/)).toBeInTheDocument();

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

  it("shows the medical history section pre-filled, and no declaration", async () => {
    renderPage();

    await screen.findByDisplayValue("Knee pain");
    expect(screen.getByText("Medical history")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Knee pain")).toBeInTheDocument();
    expect(screen.queryByText(/declaration/i)).not.toBeInTheDocument();
  });

  it("a patient with no saved history gets a blank medical history section", async () => {
    renderPage("none");

    await screen.findByText("Medical history");
    expect(screen.getByText("Medical history")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("Knee pain")).not.toBeInTheDocument();
  });
});
