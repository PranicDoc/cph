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

function renderPage() {
  mGet.mockResolvedValue({ data: patient });
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

  it("loads the patient, shows their details, and saves the changes back to the patient", async () => {
    mPut.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPage();

    const first = await screen.findByDisplayValue("Asha");
    expect(mGet).toHaveBeenCalledWith("/api/v1/patients/p-1");
    expect(screen.getByDisplayValue("Pune")).toBeInTheDocument();

    await user.clear(first);
    await user.type(first, "Ashwini");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(mPut).toHaveBeenCalled());
    expect(mPut).toHaveBeenCalledWith(
      "/api/v1/patients/p-1",
      expect.objectContaining({ firstName: "Ashwini", lastName: "Rao", contactWhatsapp: "+911111111111", city: "Pune" })
    );
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(await screen.findByText("patients-page")).toBeInTheDocument();
  });

  it("stays on the page and shows the server's reason when the save is refused", async () => {
    mPut.mockRejectedValue({ response: { data: { detail: "Contact cannot be empty" } } });
    const user = userEvent.setup();
    renderPage();

    await screen.findByDisplayValue("Asha");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Contact cannot be empty", expect.anything()));
    expect(screen.queryByText("patients-page")).not.toBeInTheDocument();
  });

  it("Cancel goes back to the patients list without saving", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByDisplayValue("Asha");
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(await screen.findByText("patients-page")).toBeInTheDocument();
    expect(mPut).not.toHaveBeenCalled();
  });
});
