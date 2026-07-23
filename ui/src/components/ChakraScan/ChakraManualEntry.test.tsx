import { useState } from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ChakraManualEntry } from "./ChakraManualEntry";
import type { ChakraCatalogEntry, ChakraDocEntry } from "./types/spatial-chakra";

const CATALOG: ChakraCatalogEntry[] = [
  {
    id: "crown",
    name: "Crown Chakra",
    category: "major",
    location: "top of head",
    side: "center",
    colorHex: "#8b5cf6",
    colorLabel: "Violet",
    bodyRegionId: "head",
    position3D: [0, 1, 0],
    displayOrder: 1,
  },
  {
    id: "heart",
    name: "Heart Chakra",
    category: "major",
    location: "chest",
    side: "center",
    colorHex: "#22c55e",
    colorLabel: "Green",
    bodyRegionId: "torso",
    position3D: [0, 0.5, 0],
    displayOrder: 2,
  },
];

function Harness({ onChangeSpy }: { onChangeSpy: (v: ChakraDocEntry[]) => void }) {
  const [value, setValue] = useState<ChakraDocEntry[]>([]);
  return (
    <ChakraManualEntry
      value={value}
      availableChakras={CATALOG}
      onChange={(next) => {
        setValue(next);
        onChangeSpy(next);
      }}
    />
  );
}

// Desktop table and mobile cards are both mounted at once (toggled by CSS
// breakpoint, not unmount) — jsdom applies no real layout, so queries that
// could match either copy are scoped to the <table>. The combobox's option
// list is portaled to document.body (see Combobox.tsx), so those are
// queried unscoped via role="option" instead.

describe("ChakraManualEntry", () => {
  it("starts empty and adds a row with a chakra combobox on '+ Add Chakra'", async () => {
    const user = userEvent.setup();
    render(<Harness onChangeSpy={vi.fn()} />);

    expect(screen.getByText(/no chakras documented yet/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /add chakra/i }));

    const table = within(screen.getByRole("table"));
    expect(table.getByPlaceholderText(/select chakra/i)).toBeInTheDocument();
  });

  it("selecting a chakra from the combobox commits a default-level entry", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(<Harness onChangeSpy={onChangeSpy} />);

    await user.click(screen.getByRole("button", { name: /add chakra/i }));
    const table = within(screen.getByRole("table"));
    await user.type(table.getByPlaceholderText(/select chakra/i), "Crown");
    await user.click(await screen.findByRole("option", { name: "Crown Chakra" }));

    expect(onChangeSpy).toHaveBeenCalledWith([
      expect.objectContaining({ id: "crown", energyLevel: "N", activationLevel: "N" }),
    ]);
    // Picker collapses to a chip once a chakra is chosen.
    expect(table.queryByPlaceholderText(/select chakra/i)).not.toBeInTheDocument();
  });

  it("an already-picked chakra doesn't appear as an option in another row", async () => {
    const user = userEvent.setup();
    render(<Harness onChangeSpy={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: /add chakra/i }));
    let table = within(screen.getByRole("table"));
    await user.type(table.getByPlaceholderText(/select chakra/i), "Crown");
    await user.click(await screen.findByRole("option", { name: "Crown Chakra" }));

    await user.click(screen.getByRole("button", { name: /add chakra/i }));
    table = within(screen.getByRole("table"));
    await user.click(table.getByPlaceholderText(/select chakra/i));

    expect(screen.queryByRole("option", { name: "Crown Chakra" })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Heart Chakra" })).toBeInTheDocument();
  });

  it("removes a row and its entry when the trash button is clicked", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(<Harness onChangeSpy={onChangeSpy} />);

    await user.click(screen.getByRole("button", { name: /add chakra/i }));
    const table = within(screen.getByRole("table"));
    await user.type(table.getByPlaceholderText(/select chakra/i), "Crown");
    await user.click(await screen.findByRole("option", { name: "Crown Chakra" }));

    await user.click(table.getByRole("button", { name: /remove chakra/i }));

    expect(onChangeSpy).toHaveBeenLastCalledWith([]);
    expect(screen.getByText(/no chakras documented yet/i)).toBeInTheDocument();
  });
});
