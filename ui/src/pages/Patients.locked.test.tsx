import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Patients from "./Patients";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

const lockedPatient = {
  id: "patient-1",
  healingCaseId: "case-1",
  firstName: "Asha",
  lastName: "Rao",
  dob: "1990-01-01",
  gender: "F",
  contact: "999",
  status: "LOCKED",
};

vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({ selectPatient: vi.fn() }),
  usePatientList: () => ({ patients: [lockedPatient], isLoading: false }),
}));
vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { id: "healer-1" } }) }));

let credits = { free_balance: 3, paid_balance: 0 };
vi.mock("../hooks/useCreditAccount", () => ({
  useCreditAccount: () => ({ data: credits }),
}));
vi.mock("../components/UpgradeModal", () => ({
  UpgradeModal: () => <div>upgrade-modal</div>,
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

describe("Patients: a LOCKED case", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    credits = { free_balance: 3, paid_balance: 0 };
  });

  it("with no credits left shows only an Upgrade button", async () => {
    credits = { free_balance: 0, paid_balance: 0 };
    const user = userEvent.setup();
    renderPatients();

    const row = screen.getAllByRole("button", { name: /upgrade/i })[0].parentElement!;
    expect(screen.queryByRole("button", { name: /^start$/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/locked:/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/generate pdf/i)).not.toBeInTheDocument();

    await user.click(within(row).getByRole("button", { name: /upgrade/i }));
    expect(screen.getByText("upgrade-modal")).toBeInTheDocument();
  });

  it("Start asks first, then spends a credit and opens the session", async () => {
    (api.post as any).mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPatients();

    await user.click(screen.getAllByRole("button", { name: /^start$/i })[0]);
    expect(screen.getByText(/uses 1 credit\. you have 3 left/i)).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: /use 1 credit and start/i }));

    expect(api.post).toHaveBeenCalledWith("/api/v1/cases/case-1/unlock");
    expect(mockNavigate).toHaveBeenCalledWith("/session/case-1");
    expect(localStorage.getItem("unlockCase.skipConfirm.healer-1")).toBe("true");
  });

  it("after Don't show again, Start unlocks without asking", async () => {
    localStorage.setItem("unlockCase.skipConfirm.healer-1", "true");
    (api.post as any).mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    renderPatients();

    await user.click(screen.getAllByRole("button", { name: /^start$/i })[0]);

    expect(screen.queryByText(/uses 1 credit\. you have/i)).not.toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith("/api/v1/cases/case-1/unlock");
  });
});
