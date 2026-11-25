import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import PatientHealthForm from "./PatientHealthForm";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
}));

vi.mock("react-router-dom", () => ({ useParams: () => ({ caseId: "case-1" }) }));

describe("PatientHealthForm: three steps", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as Mock).mockResolvedValue({ data: { status: "DRAFT", chiefComplaints: [] } });
  });

  it("asks for a complaint before moving on, and offers Submit only on the last step", async () => {
    const user = userEvent.setup();
    render(<PatientHealthForm />);
    const next = await screen.findByRole("button", { name: /^next: presenting illness$/i });
    expect(screen.queryByRole("button", { name: /submit my details/i })).not.toBeInTheDocument();

    await user.click(next);
    expect(await screen.findByText(/chief complaint is required/i)).toBeVisible();

    await user.type(screen.getByLabelText(/problem 1/i, { selector: "input" }), "Knee pain");
    await user.type(screen.getByLabelText(/duration 1/i, { selector: "input" }), "2");
    await user.click(screen.getByRole("combobox", { name: /period 1/i }));
    await user.click(await screen.findByRole("option", { name: "Days" }));
    await user.click(screen.getByRole("button", { name: /^next: presenting illness$/i }));
    await user.click(await screen.findByRole("button", { name: /^next: lab findings and more$/i }));

    expect(screen.getByRole("button", { name: /submit my details/i })).toBeVisible();
    expect(api.patch).not.toHaveBeenCalled();
  });
});
