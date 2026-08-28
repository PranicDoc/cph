import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ResourcePickerDialog } from "./ResourcePickerDialog";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

// Three resources: one grantable + not added (the only one that should be
// offered), one with zero verbs, one passed in `alreadyAdded`. The offered
// resource's label shares no letters-in-sequence with its raw enum token so
// "search by label, not token" is a meaningful assertion.
const CATALOG: RbacCatalogResource[] = [
  {
    resource: "ROLES",
    label: "Access management",
    verbs: [
      { value: "READ", label: "View", description: "View", locked: false },
      { value: "CREATE", label: "Add", description: "Add", locked: false },
      { value: "UPDATE", label: "Edit", description: "Edit", locked: false },
      { value: "DELETE", label: "Remove", description: "Remove", locked: false },
    ],
  },
  { resource: "GLOBAL_CONFIG", label: "System settings", verbs: [] },
  {
    resource: "PATIENTS",
    label: "Patient records",
    verbs: [{ value: "READ", label: "View", description: "View", locked: false }],
  },
];

function Harness({
  onConfirmSpy,
  onOpenChangeSpy,
  alreadyAdded = ["PATIENTS"],
}: {
  onConfirmSpy: (resources: string[]) => void;
  onOpenChangeSpy?: (open: boolean) => void;
  alreadyAdded?: string[];
}) {
  const [open, setOpen] = useState(true);
  return (
    <>
      <button onClick={() => setOpen(true)}>reopen</button>
      <ResourcePickerDialog
        open={open}
        onOpenChange={(o) => {
          onOpenChangeSpy?.(o);
          setOpen(o);
        }}
        catalog={CATALOG}
        alreadyAdded={alreadyAdded}
        onConfirm={onConfirmSpy}
      />
    </>
  );
}

describe("ResourcePickerDialog", () => {
  it("offers only grantable, not-yet-added resources", () => {
    render(<Harness onConfirmSpy={vi.fn()} />);

    expect(screen.getByText("Access management")).toBeInTheDocument();
    // zero-verb resource is absent
    expect(screen.queryByText("System settings")).toBeNull();
    // already-added resource is absent
    expect(screen.queryByText("Patient records")).toBeNull();
  });

  it("searches by the human label, not the raw enum token", async () => {
    const user = userEvent.setup();
    render(<Harness onConfirmSpy={vi.fn()} />);

    const search = screen.getByPlaceholderText("Search resources");

    await user.type(search, "Access");
    expect(screen.getByText("Access management")).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, "ROLES");
    expect(screen.queryByText("Access management")).toBeNull();
    expect(screen.getByText("No resources left to add.")).toBeInTheDocument();
  });

  it("keeps the dialog open when an option is selected", async () => {
    const user = userEvent.setup();
    render(<Harness onConfirmSpy={vi.fn()} />);

    await user.click(screen.getByText("Access management"));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add selected" })
    ).toBeEnabled();
  });

  it("confirm calls onConfirm with raw resource VALUES then closes", async () => {
    const user = userEvent.setup();
    const onConfirmSpy = vi.fn<(resources: string[]) => void>();
    const onOpenChangeSpy = vi.fn<(open: boolean) => void>();
    render(
      <Harness onConfirmSpy={onConfirmSpy} onOpenChangeSpy={onOpenChangeSpy} />
    );

    await user.click(screen.getByText("Access management"));
    await user.click(screen.getByRole("button", { name: "Add selected" }));

    expect(onConfirmSpy).toHaveBeenCalledTimes(1);
    expect(onConfirmSpy).toHaveBeenCalledWith(["ROLES"]);
    expect(onOpenChangeSpy).toHaveBeenLastCalledWith(false);
  });

  it("dismiss calls onOpenChange(false) and never calls onConfirm", async () => {
    const user = userEvent.setup();
    const onConfirmSpy = vi.fn();
    const onOpenChangeSpy = vi.fn<(open: boolean) => void>();
    render(
      <Harness onConfirmSpy={onConfirmSpy} onOpenChangeSpy={onOpenChangeSpy} />
    );

    await user.click(screen.getByText("Access management"));
    await user.click(
      screen.getByRole("button", { name: "Keep current resources" })
    );

    expect(onOpenChangeSpy).toHaveBeenLastCalledWith(false);
    expect(onConfirmSpy).not.toHaveBeenCalled();
  });

  it("confirm is disabled with nothing selected", () => {
    render(<Harness onConfirmSpy={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: "Add selected" })
    ).toBeDisabled();
  });

  it("reopening after a dismissal starts with an empty selection", async () => {
    const user = userEvent.setup();
    render(<Harness onConfirmSpy={vi.fn()} />);

    await user.click(screen.getByText("Access management"));
    expect(
      screen.getByRole("button", { name: "Add selected" })
    ).toBeEnabled();

    await user.click(
      screen.getByRole("button", { name: "Keep current resources" })
    );
    await user.click(screen.getByRole("button", { name: "reopen" }));

    expect(
      screen.getByRole("button", { name: "Add selected" })
    ).toBeDisabled();
  });
});
