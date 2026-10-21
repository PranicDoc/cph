import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { BrandMark } from "./BrandMark";

describe("BrandMark", () => {
  it("is announced as the HealingARK logo", () => {
    render(<BrandMark />);
    expect(screen.getByRole("img", { name: "HealingARK" })).toBeInTheDocument();
  });

  it("takes its size from the prop", () => {
    render(<BrandMark size={44} />);
    expect(screen.getByRole("img", { name: "HealingARK" })).toHaveStyle({ width: "44px", height: "44px" });
  });
});
