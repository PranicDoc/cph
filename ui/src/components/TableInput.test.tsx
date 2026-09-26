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

  // Regression test for React error #300 ("Rendered fewer hooks than expected"): the editable
  // columns useMemo used to sit AFTER the `if (readOnly) return`, so flipping readOnly on a
  // mounted table (a session leaving DRAFT, or the reverse) changed the hook count between renders.
  it("does not throw when readOnly flips on a mounted table (hook order stays stable)", () => {
    const rows = [{ medicationName: "Aspirin" }];
    const props = { value: rows, onChange: () => {}, displayKey: "medicationName" };
    const { rerender } = render(<TableInput {...props} readOnly={false} />);
    expect(screen.getByRole("textbox")).toHaveValue("Aspirin");

    rerender(<TableInput {...props} readOnly />);
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByText("Aspirin")).toBeInTheDocument();

    rerender(<TableInput {...props} readOnly={false} />);
    expect(screen.getByRole("textbox")).toHaveValue("Aspirin");
  });
});
