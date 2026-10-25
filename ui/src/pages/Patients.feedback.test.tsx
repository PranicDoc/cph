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
  usePatientList: () => ({ patients: [patient("a", "ACTIVE"), patient("c", "CLOSED")], isLoading: false }),
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

  it("is enabled only for a patient whose latest case is active, and opens the dialog for that case", async () => {
    const user = userEvent.setup();
    renderPatients();

    // Table and mobile card layouts both render; the first two are the table rows.
    const [activeButton, closedButton] = screen.getAllByRole("button", { name: /^feedback$/i });
    expect(activeButton).toBeEnabled();
    expect(closedButton).toBeDisabled();

    await user.click(activeButton);
    expect(screen.getByText("feedback-dialog:case-a")).toBeInTheDocument();
  });
});
