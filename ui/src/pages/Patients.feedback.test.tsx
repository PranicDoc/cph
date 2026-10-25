import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
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
vi.mock("../components/feedback/ManualFeedbackDialog", () => ({
  ManualFeedbackDialog: ({ healingCaseId }: { healingCaseId: string }) => (
    <div>feedback-dialog:{healingCaseId}</div>
  ),
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

  it("is offered on every started case but not on a draft or locked one", async () => {
    renderPatients();

    // The table and the phone cards both render, so each case's button appears twice.
    expect(screen.getAllByRole("button", { name: /^feedback$/i })).toHaveLength(3 * 2);
  });

  it("opens the dialog for that patient's case, closed cases included", async () => {
    const user = userEvent.setup();
    renderPatients();

    const [, closedButton] = screen.getAllByRole("button", { name: /^feedback$/i });
    await user.click(closedButton);
    expect(screen.getByText("feedback-dialog:case-c")).toBeInTheDocument();
  });
});
