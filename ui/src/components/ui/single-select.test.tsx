import { useState } from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { SingleSelect } from "./single-select";

const OPTIONS = [
  { value: "a", label: "Alpha" },
  { value: "b", label: "Beta" },
];

function Harness({ disabled }: { disabled?: boolean }) {
  const [value, setValue] = useState("");
  return (
    <SingleSelect
      aria-label="Greek"
      value={value}
      onChange={setValue}
      options={OPTIONS}
      placeholder="Pick one"
      disabled={disabled}
    />
  );
}

describe("SingleSelect", () => {
  it("shows the placeholder, then the chosen option's label", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const trigger = screen.getByRole("combobox", { name: "Greek" });
    expect(trigger).toHaveTextContent("Pick one");

    await user.click(trigger);
    await user.click(await screen.findByRole("option", { name: /Beta/ }));

    expect(trigger).toHaveTextContent("Beta");
    expect(screen.queryByRole("option", { name: /Alpha/ })).not.toBeInTheDocument();
  });

  it("does not open when disabled", async () => {
    const user = userEvent.setup();
    render(<Harness disabled />);
    await user.click(screen.getByRole("combobox", { name: "Greek" }));
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });
});
