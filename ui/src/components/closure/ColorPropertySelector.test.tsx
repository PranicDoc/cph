import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ColorPropertySelector, ColorSelection } from "./ColorPropertySelector";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";

const COLORS = ["Red", "Green", "Blue"];
const PROPERTIES: Record<string, string[]> = {
  Red: ["Heating", "Cooling"],
  Green: ["Purifying"],
  Blue: ["Soothing"],
};
const COLOR_HEX: Record<string, string> = {
  Red: "#ff0000",
  Green: "#00ff00",
  Blue: "#0000ff",
};

function Harness({ onChangeSpy }: { onChangeSpy: (v: ColorSelection[]) => void }) {
  const [value, setValue] = useState<ColorSelection[]>([]);
  return (
    <ColorPropertySelector
      value={value}
      colors={COLORS}
      getProperties={(color) => PROPERTIES[color] ?? []}
      getColorHex={(color) => COLOR_HEX[color]}
      onChange={(next) => {
        setValue(next);
        onChangeSpy(next);
      }}
    />
  );
}

function renderInsideModalDialog(onChangeSpy: (v: ColorSelection[]) => void) {
  return render(
    <Dialog open>
      <DialogContent>
        <DialogTitle>Test Dialog</DialogTitle>
        <Harness onChangeSpy={onChangeSpy} />
      </DialogContent>
    </Dialog>
  );
}

describe("ColorPropertySelector", () => {
  it("committing a color selection updates the trigger badge and enables the properties picker", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    // [0] = Colors picker, [1] = flat Properties picker (disabled until a
    // color is selected).
    const comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes).toHaveLength(2);
    expect(comboboxes[1]).toBeDisabled();

    await user.click(comboboxes[0]);
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect(onChangeSpy).toHaveBeenCalledWith([{ color: "Red", properties: [] }]);
    expect(screen.getAllByRole("combobox")[1]).not.toBeDisabled();
  });

  it("selecting a color-property pair commits it and renders a removable chip", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    await user.click(screen.getAllByRole("combobox")[0]);
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    await user.click(screen.getAllByRole("combobox")[1]);
    await user.click(await screen.findByText("Red — Heating"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect(onChangeSpy).toHaveBeenLastCalledWith([
      { color: "Red", properties: ["Heating"] },
    ]);

    const chip = screen.getByText("Red: Heating");
    expect(chip).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Remove Red Heating" }));
    expect(onChangeSpy).toHaveBeenLastCalledWith([
      { color: "Red", properties: [] },
    ]);
  });

  it("changing one color's properties leaves other selected colors' properties untouched", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    // Select both Red and Green.
    await user.click(screen.getAllByRole("combobox")[0]);
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByText("Green"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    await user.click(screen.getAllByRole("combobox")[1]);
    await user.click(await screen.findByText("Green — Purifying"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect(onChangeSpy).toHaveBeenLastCalledWith([
      { color: "Red", properties: [] },
      { color: "Green", properties: ["Purifying"] },
    ]);
  });
});
