import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { useEffect, useRef } from "react";

import PdfPinCanvas from "./PdfPinCanvas";

// A stand-in for pdf.js: two page boxes of known size, handed to the canvas like the real view does.
vi.mock("./PdfDocumentView", () => ({
  default: function FakePdf({ onPagesReady }: { onPagesReady?: (p: HTMLElement[]) => void }) {
    const ref = useRef<HTMLDivElement>(null);
    useEffect(() => {
      const pages = Array.from(ref.current!.querySelectorAll<HTMLElement>("[data-pdf-page]"));
      pages.forEach((el) =>
        el.getBoundingClientRect = () => ({ left: 100, top: 200, width: 400, height: 800, right: 500, bottom: 1000, x: 100, y: 200, toJSON: () => ({}) })
      );
      onPagesReady?.(pages);
    }, [onPagesReady]);
    return (
      <div ref={ref}>
        <div data-pdf-page="1" data-testid="page1" />
        <div data-pdf-page="2" data-testid="page2" />
      </div>
    );
  },
}));

describe("PdfPinCanvas", () => {
  it("clickAddsPinAtNormalisedCoords", () => {
    const onAddPin = vi.fn();
    render(<PdfPinCanvas fileUrl="u" pins={[]} onAddPin={onAddPin} />);

    // 150px right of the page's left edge (400 wide) and 600px below its top (800 high).
    fireEvent.click(screen.getByTestId("page2"), { clientX: 200, clientY: 440 });

    expect(onAddPin).toHaveBeenCalledTimes(1);
    const [page, x, y] = onAddPin.mock.calls[0];
    expect(page).toBe(2);
    expect(x).toBeCloseTo(0.25, 5);
    expect(y).toBeCloseTo(0.3, 5);
  });

  it("does not add a pin when read-only", () => {
    render(<PdfPinCanvas fileUrl="u" pins={[]} />);
    fireEvent.click(screen.getByTestId("page1"), { clientX: 200, clientY: 440 });
    expect(screen.queryByLabelText("New pin")).not.toBeInTheDocument();
  });

  it("draws pins at their normalised position and opens the thread on click", async () => {
    const onSelectPin = vi.fn();
    const onAddPin = vi.fn();
    render(
      <PdfPinCanvas fileUrl="u" pins={[{ id: "c1", page: 1, x: 0.5, y: 0.25, label: "1" }]}
        onSelectPin={onSelectPin} onAddPin={onAddPin} />
    );
    const pin = await screen.findByRole("button", { name: "Pin 1" });
    expect(pin).toHaveStyle({ left: "50%", top: "25%" });

    fireEvent.click(pin);

    expect(onSelectPin).toHaveBeenCalledWith("c1");
    expect(onAddPin).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByTestId("page1")).toContainElement(pin));
  });
});
