import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Session from "./Session";
import api from "../lib/axios";
import { buildSessionSchema } from "./schemas/sessionSchema";
import { splitClosureComments } from "./Closure";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => vi.fn(), useParams: () => ({ healingCaseId: "case-1" }) };
});

vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({ selectPatient: vi.fn(), selectedPatient: null }),
}));

vi.mock("../hooks/admin/useMasterData", () => ({
  useProtocols: () => ({ data: [] }),
}));

// The wizard itself is covered elsewhere; here we only check which session it is opened with.
vi.mock("../components/HealingSession", () => ({
  HealingSessionWizard: ({ session }: { session?: { id: string } }) => (
    <div>wizard for {session ? session.id : "a new session"}</div>
  ),
}));

const pastSession = {
  id: "session-1",
  sessionNumber: 1,
  scans: [
    { id: "scan-pre", scanType: "PRE", readings: [{ id: "r1", chakraName: "Crown", energyLevel: "N", activationLevel: "N" }] },
    { id: "scan-post", scanType: "POST", readings: [{ id: "r2", chakraName: "Crown", energyLevel: "C", activationLevel: "N" }] },
  ],
};

const caseWithStatus = (status: string) => ({
  id: "case-1",
  patientId: "patient-1",
  status,
  medicalCondition: "Neck pain",
  symptoms: [],
  medications: [],
  labFindings: [],
  healingDetails: { protocolName: "Headache", bookReference: "", detailedProtocol: "", treatmentSteps: [] },
  healingSession: [pastSession],
});

function renderSession() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/session/case-1"]}>
        <Session />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Session: a case can be edited on any status", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows the edit pencil and Edit closure on a closed case", async () => {
    (api.get as any).mockResolvedValue({ data: caseWithStatus("CLOSED") });
    renderSession();

    expect(await screen.findByRole("button", { name: /edit session/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /edit closure/i })).toBeInTheDocument();
  });

  it("has no Edit closure while the case is still active", async () => {
    (api.get as any).mockResolvedValue({ data: caseWithStatus("ACTIVE") });
    renderSession();

    expect(await screen.findByRole("button", { name: /edit session/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /edit closure/i })).not.toBeInTheDocument();
  });

  it("opens a past session in the wizard to correct it", async () => {
    const user = userEvent.setup();
    (api.get as any).mockResolvedValue({ data: caseWithStatus("CLOSED") });
    renderSession();

    await user.click(await screen.findByText("Session 1"));
    await user.click(screen.getByRole("button", { name: /^edit$/i }));

    expect(screen.getByText("wizard for session-1")).toBeInTheDocument();
  });
});

describe("Session: saving case details", () => {
  beforeEach(() => vi.clearAllMocks());

  it("Save stores the details and returns to the view, which offers the next step", async () => {
    const user = userEvent.setup();
    (api.get as Mock).mockResolvedValue({ data: caseWithStatus("ACTIVE") });
    (api.patch as Mock).mockResolvedValue({ data: caseWithStatus("ACTIVE") });
    renderSession();

    await user.click(await screen.findByRole("button", { name: /edit session/i }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(api.patch).toHaveBeenCalledWith("/api/v1/cases", expect.any(FormData), expect.anything());
    expect(await screen.findByRole("button", { name: /proceed to session/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^save$/i })).not.toBeInTheDocument();
  });

  it("stays in edit mode when saving fails", async () => {
    const user = userEvent.setup();
    (api.get as Mock).mockResolvedValue({ data: caseWithStatus("ACTIVE") });
    (api.patch as Mock).mockRejectedValue(new Error("network"));
    renderSession();

    await user.click(await screen.findByRole("button", { name: /edit session/i }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByRole("button", { name: /^save$/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /proceed to session/i })).not.toBeInTheDocument();
  });
});

describe("Case details form", () => {
  it("keeps medications and lab findings editable whatever the status", () => {
    const fields = buildSessionSchema([]).flatMap((section) => section.fields);
    expect(fields.find((f) => f.name === "medications")?.readOnly).toBeFalsy();
    expect(fields.find((f) => f.name === "labFindings")?.readOnly).toBeFalsy();
  });
});

describe("splitClosureComments", () => {
  it("separates the saved protocol line from the healer's comments", () => {
    expect(splitClosureComments("The patient is advised to follow: \nGeneral Sweeping, Energy Hygiene\n\nFully healed")).toEqual({
      protocolNames: "General Sweeping, Energy Hygiene",
      comments: "Fully healed",
    });
  });

  it("leaves comments without a protocol line untouched", () => {
    expect(splitClosureComments("Fully healed")).toEqual({ protocolNames: "", comments: "Fully healed" });
  });
});
