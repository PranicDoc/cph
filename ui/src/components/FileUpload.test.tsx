import { useState } from "react";
import { describe, it, expect } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { FileUpload } from "./FileUPload";

const SAVED_KEY = "lab-findings/case-1/blood-report.pdf";

function Harness({ confirmRemove }: { confirmRemove?: boolean }) {
  const [value, setValue] = useState<(File | string)[]>([SAVED_KEY]);
  return <FileUpload value={value} onChange={setValue} confirmRemove={confirmRemove} />;
}

describe("FileUpload remove", () => {
  it("asks for confirmation before removing a lab finding when confirmRemove is set", async () => {
    const user = userEvent.setup();
    render(<Harness confirmRemove />);

    await user.click(screen.getByRole("button", { name: `Remove ${SAVED_KEY}` }));

    const dialog = screen.getByRole("alertdialog");
    expect(within(dialog).getByText("blood-report.pdf")).toBeInTheDocument();
    // Not removed yet: still listed outside the dialog.
    expect(screen.getAllByText("blood-report.pdf", { selector: "span" })).toHaveLength(2);
  });

  it("removes the file once the healer confirms", async () => {
    const user = userEvent.setup();
    render(<Harness confirmRemove />);

    await user.click(screen.getByRole("button", { name: `Remove ${SAVED_KEY}` }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(screen.queryByText("blood-report.pdf", { selector: "span" })).not.toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("keeps the file when the healer cancels", async () => {
    const user = userEvent.setup();
    render(<Harness confirmRemove />);

    await user.click(screen.getByRole("button", { name: `Remove ${SAVED_KEY}` }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("blood-report.pdf", { selector: "span" })).toBeInTheDocument();
  });

  it("removes immediately without confirmRemove (patient self-service form)", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: `Remove ${SAVED_KEY}` }));

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.queryByText("blood-report.pdf", { selector: "span" })).not.toBeInTheDocument();
  });
});

describe("FileUpload size limit", () => {
  it("adds a normal photo but rejects one over 10 MB with a message", async () => {
    const user = userEvent.setup({ applyAccept: false });
    const { container } = render(<Harness />);
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    const small = new File([new Uint8Array(3 * 1024 * 1024)], "scan.png", { type: "image/png" });
    const big = new File([new Uint8Array(10 * 1024 * 1024 + 1)], "huge.png", { type: "image/png" });

    await user.upload(input, [small, big]);

    expect(screen.getByText("scan.png")).toBeInTheDocument();
    expect(screen.queryByText("huge.png", { selector: "span" })).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("huge.png is larger than 10.0 MB");
  });
});
