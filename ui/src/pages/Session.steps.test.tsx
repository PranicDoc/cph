import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Session from "./Session";
import api from "../lib/axios";

vi.mock("../hooks/useCaseReviewEnabled", () => ({ useCaseReviewEnabled: () => true }));
vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
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

const draft = (chiefComplaints: unknown[] = [], protocolName = "") => ({
  id: "case-1",
  patientId: "patient-1",
  status: "DRAFT",
  chiefComplaints,
  symptoms: [],
  medications: [],
  labFindings: [],
  healingDetails: protocolName
    ? { protocolName, bookReference: "", detailedProtocol: "", treatmentSteps: [] }
    : null,
  healingSession: [],
});

const neck = { id: "cc-1", problem: "Neck pain", durationValue: 5, durationUnit: "DAYS" };

function open(data: unknown) {
  (api.get as Mock).mockResolvedValue({ data });
  (api.patch as Mock).mockResolvedValue({ data });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Session />
      </MemoryRouter>
    </QueryClientProvider>
  );
  return userEvent.setup();
}

const button = (name: RegExp) => screen.getByRole("button", { name });
const noButton = (name: RegExp) => expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
const nextToIllness = /^next: presenting illness$/i;
const nextToRest = /^next: lab findings and more$/i;

describe("Session: the case details are three steps", () => {
  beforeEach(() => vi.clearAllMocks());

  it("opens on chief complaints, with the other steps out of reach until a problem is complete", async () => {
    const user = open(draft());
    await screen.findByRole("button", { name: nextToIllness });
    expect(button(/add problem/i)).toBeVisible();
    noButton(/^save$/i);
    noButton(/choose a problem/i);

    await user.click(button(nextToIllness));

    expect(await screen.findByText(/chief complaint is required/i)).toBeVisible();
    expect(button(nextToIllness)).toBeVisible();

    await user.click(button(/3\s*lab findings and more/i));
    expect(button(nextToIllness)).toBeVisible();
    noButton(/^save$/i);
    expect(api.patch).not.toHaveBeenCalled();
  });

  it("walks forward and back, and saves everything once from the last step", async () => {
    const user = open(draft([neck], "Headache"));
    await user.click(await screen.findByRole("button", { name: nextToIllness }));

    await user.click(screen.getByRole("radio", { name: "Sudden" }));
    expect(api.patch).not.toHaveBeenCalled();
    await user.click(button(nextToRest));
    expect(button(/^save$/i)).toBeVisible();

    await user.click(button(/back to presenting illness/i));
    expect(screen.getByRole("radio", { name: "Sudden" })).toHaveAttribute("aria-checked", "true");
    await user.click(button(/back to chief complaints/i));
    expect(screen.getByDisplayValue("Neck pain")).toBeVisible();

    await user.click(button(/3\s*lab findings and more/i));
    await user.click(button(/^save$/i));

    expect(api.patch).toHaveBeenCalledTimes(1);
    const body = (api.patch as Mock).mock.calls[0][1] as FormData;
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.readAsText(body.get("data") as Blob);
    });
    expect(JSON.parse(text).chiefComplaints[0]).toMatchObject({
      problem: "Neck pain",
      problemHistory: { onset: "SUDDEN" },
    });
  });

  it("stays on presenting illness, at the problem concerned, while a pattern is not described", async () => {
    const knee = { id: "cc-2", problem: "Knee pain", durationValue: 2, durationUnit: "DAYS", timing: "PATTERN" };
    const user = open(draft([neck, knee], "Headache"));
    await screen.findByRole("button", { name: nextToIllness });

    await user.click(button(/3\s*lab findings and more/i));

    expect(await screen.findByText("Describe the pattern for this problem")).toBeVisible();
    expect(screen.getByRole("heading", { name: "Knee pain" })).toBeVisible();
    expect(button(nextToRest)).toBeVisible();
    noButton(/^save$/i);

    await user.type(screen.getByLabelText(/describe the pattern/i), "after walking");
    await user.click(button(nextToRest));
    expect(button(/^save$/i)).toBeVisible();
    expect(api.patch).not.toHaveBeenCalled();
  });

  it("never saves from an earlier step: Enter stays put, and a form submit only moves on", async () => {
    const user = open(draft([neck], "Headache"));
    const problem = await screen.findByDisplayValue("Neck pain");

    await user.type(problem, "{Enter}");
    expect(button(nextToIllness)).toBeVisible();

    fireEvent.submit(problem.closest("form")!);

    expect(await screen.findByRole("button", { name: nextToRest })).toBeVisible();
    expect(api.patch).not.toHaveBeenCalled();
  });

  it("scrolls to the error that is on screen when a save is refused", async () => {
    const scrolled: Element[] = [];
    Element.prototype.scrollIntoView = function () { scrolled.push(this); };
    const user = open(draft([neck]));
    await user.click(await screen.findByRole("button", { name: /3\s*lab findings and more/i }));

    await user.click(button(/^save$/i));

    const message = await screen.findByText(/select at least one protocol/i);
    await vi.waitFor(() => expect(scrolled.length).toBeGreaterThan(0));
    expect(scrolled[scrolled.length - 1]).toBe(message);
  });
});
