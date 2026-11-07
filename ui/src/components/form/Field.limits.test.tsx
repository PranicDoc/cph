import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";

import { CharacterCount } from "./CharacterCount";
import { SelectField, TextField, TextareaField, type TanstackField } from "./Field";

const field = (value: string): TanstackField => ({
  state: { value, meta: { errors: [] } },
  handleChange: () => {},
  handleBlur: () => {},
});

describe("typed field limits", () => {
  it("a limited text area stops typing at the limit and shows the live count", () => {
    render(<TextareaField field={field("hello")} maxLength={200} />);

    expect(screen.getByRole("textbox")).toHaveAttribute("maxlength", "200");
    expect(screen.getByText("5/200")).toBeInTheDocument();
  });

  it("a text area with no limit shows no count", () => {
    render(<TextareaField field={field("hello")} />);

    expect(screen.getByRole("textbox")).not.toHaveAttribute("maxlength");
    expect(screen.queryByText(/\/\d+$/)).not.toBeInTheDocument();
  });

  it("a text field shows its count only when asked, so inline layouts keep a single element", () => {
    const { rerender } = render(<TextField field={field("abc")} maxLength={100} />);
    expect(screen.getByRole("textbox")).toHaveAttribute("maxlength", "100");
    expect(screen.queryByText("3/100")).not.toBeInTheDocument();

    rerender(<TextField field={field("abc")} maxLength={100} showCount />);
    expect(screen.getByText("3/100")).toBeInTheDocument();
  });

  it("the count turns red over the limit, for text saved before the limit existed", () => {
    render(<CharacterCount value={"x".repeat(11)} max={10} />);

    expect(screen.getByText("11/10")).toHaveClass("text-danger");
  });
});

describe("SelectField hidden options", () => {
  const options = [
    { label: "Good", value: "GOOD" },
    { label: "Fair", value: "FAIR", hidden: true },
  ];

  it("still shows a hidden option as the current value, but does not offer it for picking", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    render(<SelectField field={field("FAIR")} options={options} />);

    expect(screen.getByRole("combobox")).toHaveTextContent("Fair");
    await userEvent.setup().click(screen.getByRole("combobox"));
    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual(["Good", "Fair"]);
  });

  it("does not offer a hidden option when it is not the current value", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    render(<SelectField field={field("GOOD")} options={options} />);

    await userEvent.setup().click(screen.getByRole("combobox"));
    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual(["Good"]);
  });
});
