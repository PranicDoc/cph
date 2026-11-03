import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Patients from "./Patients";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
}));

const patient = (id: string, status: string) => ({
  id,
  healingCaseId: `case-${id}`,
  firstName: `Patient ${id}`,
  lastName: "",
  fullName: `Patient ${id}`,
  dob: "1990-01-01",
  gender: "F",
  contact: "999",
  status,
});

vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({ selectPatient: vi.fn() }),
  usePatientList: () => ({
    patients: [patient("a", "ACTIVE"), patient("c", "CLOSED"), patient("n", "CONTINUED"), patient("d", "DRAFT"), patient("l", "LOCKED")],
    isLoading: false,
  }),
}));
vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { id: "healer-1" } }) }));
vi.mock("../hooks/useCreditAccount", () => ({
  useCreditAccount: () => ({ data: { free_balance: 3, paid_balance: 0 } }),
}));

function renderPatients() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Patients />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Patients: manual feedback action", () => {
  beforeEach(() => vi.clearAllMocks());

  const menuItems = async (user: ReturnType<typeof userEvent.setup>, name: string) => {
    await user.click(screen.getAllByRole("button", { name: new RegExp(`actions for ${name}`, "i") })[0]);
    return screen.getAllByRole("menuitem").map((el) => el.textContent);
  };

  it("is offered on every started case but not on a draft or locked one", async () => {
    const user = userEvent.setup();
    renderPatients();

    for (const started of ["Patient a", "Patient c", "Patient n"]) {
      expect(await menuItems(user, started)).toContain("Feedback");
      await user.keyboard("{Escape}");
    }
    for (const notStarted of ["Patient d", "Patient l"]) {
      expect(await menuItems(user, notStarted)).not.toContain("Feedback");
      await user.keyboard("{Escape}");
    }
  });

  it("opens the feedback page for that patient's case, closed cases included", async () => {
    const user = userEvent.setup();
    renderPatients();

    await menuItems(user, "Patient c");
    await user.click(screen.getByRole("menuitem", { name: /^feedback$/i }));
    expect(mockNavigate).toHaveBeenCalledWith("/cases/case-c/feedback", {
      state: { patientName: "Patient c" },
    });
  });

  it("keeps every action in the menu and opens the case when the row is clicked", async () => {
    const user = userEvent.setup();
    renderPatients();

    expect(await menuItems(user, "Patient c")).toEqual([
      "View", "Generate PDF", "Feedback", "Case Summary", "Edit patient", "Delete patient",
    ]);
    await user.keyboard("{Escape}");
    expect(await menuItems(user, "Patient a")).toContain("Complete");
    await user.keyboard("{Escape}");

    await user.click(screen.getAllByText("Patient c")[0]);
    expect(mockNavigate).toHaveBeenCalledWith("/session/case-c");
  });
});
