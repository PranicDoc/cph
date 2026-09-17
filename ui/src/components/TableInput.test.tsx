import { useState } from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { TableInput } from "./TableInput";

function Harness() {
  const [value, setValue] = useState<any[]>([{ medicationName: "" }]);
  return (
    <TableInput
      value={value}
      onChange={setValue}
      displayKey="medicationName"
      placeholder="Add Medication"
    />
  );
}

describe("TableInput", () => {
  // Regression test: the editable-view `columns` array used to be rebuilt fresh on every
  // render with updateCell/deleteRow closed over directly, which made TanStack's flexRender
  // treat each cell as a brand-new component type and fully remount the underlying <input> on
  // every keystroke -- so typing more than one character required re-clicking into the field
  // each time. Fixed by keeping `columns` referentially stable and routing updateCell/deleteRow
  // through the DataTable `meta` prop instead.
  it("accepts multiple consecutive characters into a text cell without losing focus", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    const input = screen.getByRole("textbox");
    await user.click(input);
    await user.type(input, "Paracetamol");

    expect(input).toHaveValue("Paracetamol");
    expect(input).toHaveFocus();
  });
});
