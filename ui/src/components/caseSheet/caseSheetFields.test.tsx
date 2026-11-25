import { useState } from "react";
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChiefComplaintsField } from "./ChiefComplaintsField";
import { PresentingIllnessField } from "./PresentingIllnessField";
import { BmiValue, ChiefComplaintsView, PresentingIllnessView } from "./CaseSheetViews";
import { InfoTip } from "../InfoTip";
import { type ChiefComplaintRow, emptyComplaintRow } from "@/lib/chiefComplaints";

function ComplaintsHarness({ initial }: { initial: ChiefComplaintRow[] }) {
  const [rows, setRows] = useState(initial);
  return (
    <>
      <ChiefComplaintsField value={rows} onChange={setRows} />
      <output data-testid="count">{rows.length}</output>
    </>
  );
}

function IllnessHarness({ initial }: { initial: ChiefComplaintRow[] }) {
  const [rows, setRows] = useState(initial);
  return (
    <>
      <PresentingIllnessField value={rows} onChange={setRows} />
      <output data-testid="history">{JSON.stringify(rows.map((r) => r.problemHistory ?? null))}</output>
    </>
  );
}

const complete = (problem: string, id: string): ChiefComplaintRow =>
  ({ id, problem, durationValue: 3, durationUnit: "MONTHS" });

describe("ChiefComplaintsField", () => {
  it("adds rows up to ten, then stops", async () => {
    const user = userEvent.setup();
    render(<ComplaintsHarness initial={[emptyComplaintRow()]} />);
    const add = screen.getByRole("button", { name: /add problem/i });
    for (let i = 0; i < 12; i++) if (!add.hasAttribute("disabled")) await user.click(add);
    expect(screen.getByTestId("count")).toHaveTextContent("10");
    expect(add).toBeDisabled();
    expect(screen.getByText(/up to 10 problems/i)).toBeInTheDocument();
  });

  it("removes a row but always keeps one to type in", async () => {
    const user = userEvent.setup();
    render(<ComplaintsHarness initial={[complete("Headache", "a")]} />);
    await user.click(screen.getByRole("button", { name: /remove problem 1/i }));
    expect(screen.getByTestId("count")).toHaveTextContent("1");
    expect(screen.getByLabelText(/problem 1/i, { selector: "input" })).toHaveValue("");
  });

  it("flags a problem without a duration once the person leaves the row", async () => {
    const user = userEvent.setup();
    render(<ComplaintsHarness initial={[emptyComplaintRow()]} />);
    const problem = screen.getByLabelText(/problem 1/i, { selector: "input" });
    await user.type(problem, "Knee pain");
    expect(screen.queryByText(/enter a duration/i)).not.toBeInTheDocument();
    await user.tab();
    expect(await screen.findByText(/enter a duration/i)).toBeInTheDocument();
  });

  it("tags a legacy row with Duration missing", () => {
    render(<ComplaintsHarness initial={[{ id: "l", problem: "Back pain", durationValue: "", durationUnit: "", legacy: true }]} />);
    expect(screen.getByText("Duration missing")).toBeInTheDocument();
  });
});

describe("PresentingIllnessField", () => {
  it("asks for a complaint first when no problem is complete", () => {
    render(<IllnessHarness initial={[{ problem: "Pain", durationValue: "", durationUnit: "" }]} />);
    expect(screen.getByText(/add a chief complaint/i)).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("shows the pattern box only when timing is pattern, and stores the answers on the problem", async () => {
    const user = userEvent.setup();
    render(<IllnessHarness initial={[complete("Headache", "a"), complete("Knee pain", "b")]} />);
    expect(screen.getByText("Problem 1 of 2 · 0 filled")).toBeInTheDocument();
    expect(screen.queryByLabelText(/describe the pattern/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Gradual" }));
    await user.click(screen.getByRole("radio", { name: "Pattern" }));
    await user.type(screen.getByLabelText(/describe the pattern/i), "after 3 pm");
    const stored = JSON.parse(screen.getByTestId("history").textContent ?? "[]");
    expect(stored[0]).toMatchObject({ onset: "GRADUAL", timing: "PATTERN", timingPattern: "after 3 pm" });
    expect(stored[1]).toBeNull();

    await user.click(screen.getByRole("radio", { name: "Constant" }));
    expect(screen.queryByLabelText(/describe the pattern/i)).not.toBeInTheDocument();
  });

  it("requires the pattern description, and says so once the box is left empty", async () => {
    const user = userEvent.setup();
    render(<IllnessHarness initial={[complete("Headache", "a")]} />);
    await user.click(screen.getByRole("radio", { name: "Pattern" }));
    const box = screen.getByLabelText(/describe the pattern/i);
    expect(box).toBeRequired();
    expect(screen.queryByText("Describe the pattern for this problem")).not.toBeInTheDocument();

    await user.click(box);
    await user.tab();
    expect(screen.getByText("Describe the pattern for this problem")).toBeInTheDocument();
    expect(box).toHaveAttribute("aria-invalid", "true");

    await user.type(box, "mornings");
    expect(screen.queryByText("Describe the pattern for this problem")).not.toBeInTheDocument();
    expect(box).not.toHaveAttribute("aria-invalid", "true");
  });

  it("asks what it feels like in one box, with the guidance written inside it", () => {
    render(<IllnessHarness initial={[complete("Headache", "a")]} />);
    expect(screen.getByRole("textbox", { name: "What it feels like" })).toHaveAttribute(
      "placeholder", "Describe the problem in your own words: sharp, dull, burning, throbbing, heavy and so on.");
    expect(screen.queryByText("Character")).not.toBeInTheDocument();
    for (const name of ["Location", "Makes it worse", "Makes it better"]) {
      expect(screen.getByRole("textbox", { name }).getAttribute("placeholder")).toBeTruthy();
    }
  });

  it("clears an answer when the chosen option is chosen again", async () => {
    const user = userEvent.setup();
    render(<IllnessHarness initial={[complete("Headache", "a")]} />);
    await user.click(screen.getByRole("radio", { name: "Sudden" }));
    await user.click(screen.getByRole("radio", { name: "Sudden" }));
    expect(JSON.parse(screen.getByTestId("history").textContent ?? "[]")[0].onset).toBeNull();
  });
});

describe("InfoTip", () => {
  it("is drawn as the information icon", () => {
    render(<InfoTip text="Help" label="About height" />);
    const trigger = screen.getByRole("button", { name: "About height" });
    expect(trigger.querySelector("svg.lucide-info")).not.toBeNull();
    expect(trigger).toHaveTextContent("");
  });

  it("opens its text on click and closes it on a second tap", async () => {
    const user = userEvent.setup();
    render(<InfoTip text="Enter your height in centimetres." label="About height" />);
    expect(screen.queryByText(/centimetres/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "About height" }));
    expect(await screen.findByText(/centimetres/i)).toBeInTheDocument();
  });
});

describe("read-only views", () => {
  it("prints a complaint line and tags a row without a duration", () => {
    render(<ChiefComplaintsView complaints={[
      { problem: "Headache", durationValue: 3, durationUnit: "MONTHS" },
      { problem: "Back pain", durationValue: null, durationUnit: null },
    ]} />);
    expect(screen.getByText("Headache since 3 months")).toBeInTheDocument();
    expect(screen.getByText("Back pain")).toBeInTheDocument();
    expect(screen.getByText("Duration missing")).toBeInTheDocument();
  });

  it("keeps presenting illness behind a view link, and shows each problem's answers in a popup", async () => {
    const user = userEvent.setup();
    render(<PresentingIllnessView complaints={[
      { problem: "Headache", durationValue: 3, durationUnit: "MONTHS", onset: "GRADUAL", timing: "PATTERN", timingPattern: "after 3 pm" },
      { problem: "Poor sleep", durationValue: 1, durationUnit: "YEARS" },
    ]} />);
    expect(screen.queryByText("Gradual")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Click to view" }));

    expect(screen.getByRole("dialog", { name: "Presenting illness" })).toBeInTheDocument();
    expect(screen.getByText("Gradual")).toBeInTheDocument();
    expect(screen.getByText("Pattern: after 3 pm")).toBeInTheDocument();
    expect(screen.getByText("No details entered.")).toBeInTheDocument();
  });

  it("offers no presenting illness link when nothing was answered", () => {
    render(<PresentingIllnessView complaints={[{ problem: "Headache", durationValue: 3, durationUnit: "MONTHS" }]} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByText("\u2014")).toBeInTheDocument();
  });

  it("shows BMI with its tag, or Not provided", () => {
    const { rerender } = render(<BmiValue bmi={27.1} category="Overweight" />);
    expect(screen.getByText("27.1")).toBeInTheDocument();
    expect(screen.getByText("Overweight")).toBeInTheDocument();
    rerender(<BmiValue bmi={null} category={null} />);
    expect(screen.getByText("Not provided")).toBeInTheDocument();
  });
});
