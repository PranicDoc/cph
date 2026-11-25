import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Session from "./Session";
import api from "../lib/axios";

vi.mock("../hooks/useCaseReviewEnabled", () => ({ useCaseReviewEnabled: () => true }));
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
  chiefComplaints: [],
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

/** The Save button is on the last step of the case details. */
async function goToLastStep(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: /3\s*lab findings and more/i }));
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

    await user.click(await screen.findByRole("button", { name: /^next: presenting illness$/i }));

    // The chief complaint table shows its own error, and the form does not move on to Save.
    expect((await screen.findAllByText(/chief complaint is required/i)).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /^save$/i })).not.toBeInTheDocument();
    expect(api.patch).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("still blocks when only the chief complaint is filled (protocol missing)", async () => {
    const user = userEvent.setup();
    (api.get as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { ...draftCase, chiefComplaints: [{ id: "cc-1", problem: "Neck pain", durationValue: 5, durationUnit: "DAYS" }] },
    });
    renderSession();

    expect(await screen.findByDisplayValue("Neck pain")).toBeInTheDocument();
    await goToLastStep(user);
    const save = screen.getByRole("button", { name: /^save$/i });
    expect(save).toHaveAttribute("aria-disabled", "true");
    await user.click(save);

    await waitFor(() => {
      expect(screen.getByText(/select at least one protocol/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/chief complaint is required/i)).not.toBeInTheDocument();
    expect(api.patch).not.toHaveBeenCalled();
  });
});

describe("Session step 0 — an already filled protocol is shown when the case is edited", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("keeps a stored protocol name that is not in the catalog (e.g. Headache) and counts it as a protocol", async () => {
    (api.get as any).mockResolvedValue({
      data: {
        ...draftCase,
        chiefComplaints: [{ id: "cc-1", problem: "Neck pain", durationValue: 5, durationUnit: "DAYS" }],
        healingDetails: { protocolName: "Headache", bookReference: "", detailedProtocol: "", treatmentSteps: [] },
      },
    });
    renderSession();

    await goToLastStep(userEvent.setup());
    expect((await screen.findAllByText("Headache")).length).toBeGreaterThan(0);
    const startButton = screen.getByRole("button", { name: /^save$/i });
    expect(startButton).not.toHaveAttribute("aria-disabled", "true");
  });

  it("shows a catalog protocol with its book and page", async () => {
    (api.get as any).mockResolvedValue({
      data: {
        ...draftCase,
        chiefComplaints: [{ id: "cc-1", problem: "Toothache", durationValue: 2, durationUnit: "DAYS" }],
        healingDetails: { protocolName: "Toothache", bookReference: "", detailedProtocol: "", treatmentSteps: [] },
      },
    });
    renderSession();

    expect(await screen.findByText(/Toothache — Advanced Pranic Healing, p\.76/)).toBeInTheDocument();
  });
});

describe("Session: a case that was not started yet", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows the locked message instead of the form when the server answers 423", async () => {
    (api.get as any).mockRejectedValue({ response: { status: 423 } });
    renderSession();

    expect(await screen.findByText(/this case is locked/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^save$/i })).not.toBeInTheDocument();
  });
});
