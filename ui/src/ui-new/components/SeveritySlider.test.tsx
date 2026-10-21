import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SeveritySlider, severityBand, severityToRating } from "./SeveritySlider";

describe("SeveritySlider", () => {
  it("stores severity as a rating where higher is better", () => {
    expect(severityToRating(0)).toBe(10);
    expect(severityToRating(10)).toBe(0);
    expect(severityToRating(7)).toBe(3);
  });
  it("names the band", () => {
    expect(severityBand(0).label).toBe("None");
    expect(severityBand(2).label).toBe("Mild");
    expect(severityBand(5).label).toBe("Moderate");
    expect(severityBand(8).label).toBe("Severe");
    expect(severityBand(10).label).toBe("Worst");
  });
  it("shows not rated until touched and reports the chosen value", () => {
    const onChange = vi.fn();
    render(<SeveritySlider label="Headache" value={undefined} onChange={onChange} />);
    expect(screen.getByText("Not rated yet")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("slider", { name: /Headache/ }), { target: { value: "6" } });
    expect(onChange).toHaveBeenCalledWith(6);
  });
});
