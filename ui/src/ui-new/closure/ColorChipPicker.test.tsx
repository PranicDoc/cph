import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ColorChipPicker } from "./ColorChipPicker";

const props = (over = {}) => ({
  label: "Energising colors",
  value: [],
  colors: ["Red", "Blue"],
  getProperties: (c: string) => (c === "Red" ? ["Heating", "Cooling"] : []),
  onChange: vi.fn(),
  ...over,
});

describe("ColorChipPicker", () => {
  it("adds a colour with no properties when its chip is pressed", async () => {
    const p = props();
    render(<ColorChipPicker {...p} />);
    await userEvent.click(screen.getByRole("button", { name: "Red" }));
    expect(p.onChange).toHaveBeenCalledWith([{ color: "Red", properties: [] }]);
  });

  it("toggles a property of a picked colour and shows the count", async () => {
    const p = props({ value: [{ color: "Red", properties: ["Heating"] }] });
    render(<ColorChipPicker {...p} />);
    expect(screen.getByText("1 of 2 properties")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cooling" }));
    expect(p.onChange).toHaveBeenCalledWith([{ color: "Red", properties: ["Heating", "Cooling"] }]);
  });

  it("removes a colour with its properties when its chip is pressed again", async () => {
    const p = props({ value: [{ color: "Red", properties: ["Heating"] }] });
    render(<ColorChipPicker {...p} />);
    await userEvent.click(screen.getAllByRole("button", { name: "Red" })[0]);
    expect(p.onChange).toHaveBeenCalledWith([]);
  });
});
