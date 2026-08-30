import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Session from "./Session";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => ({ healingCaseId: "case-1" }),
  };
});

vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({ selectPatient: vi.fn(), selectedPatient: null }),
}));

vi.mock("../hooks/admin/useMasterData", () => ({
  useProtocols: () => ({
    data: [
      { id: "p1", name: "Toothache", sourceBook: "Advanced Pranic Healing", pageNumber: 76 },
    ],
  }),
}));

const draftCase = {
  id: "case-1",
  patientId: "patient-1",
  status: "DRAFT",
  medicalCondition: "",
  symptoms: [],
  medications: [],
  labFindings: [],
  complementaryDetails: "",
  infectiousDetails: "",
  healingDetails: null,
  healingSession: [],
};

function renderSession() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/session/case-1"]}>
        <Session />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Session step 0 — mandatory fields before Start Session", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as any).mockResolvedValue({ data: draftCase });
    (api.patch as any).mockResolvedValue({ data: draftCase });
  });

  it("does not submit an empty form and shows the required-field errors", async () => {
    const user = userEvent.setup();
    renderSession();

    const startButton = await screen.findByRole("button", { name: /start session/i });
    expect(startButton).toHaveAttribute("aria-disabled", "true");

    await user.click(startButton);

    await waitFor(() => {
      expect(screen.getByText(/select at least one protocol/i)).toBeInTheDocument();
    });
    // Chief Complaint (a `required` field) shows its own error too.
    expect(screen.getByText(/^required$/i)).toBeInTheDocument();
    expect(api.patch).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("still blocks when only the chief complaint is filled (protocol missing)", async () => {
    const user = userEvent.setup();
    renderSession();

    const chiefComplaint = await screen.findByPlaceholderText(/stomachache for 4 days/i);
    await user.type(chiefComplaint, "Neck pain for 5 days");
    await user.click(screen.getByRole("button", { name: /start session/i }));

    await waitFor(() => {
      expect(screen.getByText(/select at least one protocol/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/chief complaint is required/i)).not.toBeInTheDocument();
    expect(api.patch).not.toHaveBeenCalled();
  });
});
