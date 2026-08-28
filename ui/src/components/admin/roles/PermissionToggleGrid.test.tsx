import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import {
  PermissionToggleGrid,
  type GrantMap,
} from "./PermissionToggleGrid";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

const vb = (value: string, label: string, locked = false) => ({
  value,
  label,
  description: `${label} action`,
  locked,
});

// One resource supports 4 verbs, the other supports 2. Labels deliberately
// share no substring with their raw enum tokens so a "no raw token in the DOM"
// assertion is meaningful.
const CATALOG: RbacCatalogResource[] = [
  {
    resource: "ROLES",
    label: "Access management",
    verbs: [vb("READ", "View"), vb("CREATE", "Add"), vb("UPDATE", "Edit"), vb("DELETE", "Remove")],
  },
  {
    resource: "LEGAL_DOCUMENTS",
    label: "Legal library",
    verbs: [vb("READ", "View"), vb("EXPORT", "Download")],
  },
];

function Grid({
  initial = { ROLES: [], LEGAL_DOCUMENTS: [] },
  onChangeSpy,
  onRemoveSpy,
}: {
  initial?: GrantMap;
  onChangeSpy?: (next: GrantMap, prev: GrantMap) => void;
  onRemoveSpy?: (resource: string) => void;
}) {
  const [value, setValue] = useState<GrantMap>(initial);
  return (
    <PermissionToggleGrid
      catalog={CATALOG}
      value={value}
      onChange={(next) => {
        onChangeSpy?.(next, value);
        setValue(next);
      }}
      onRemoveResource={(resource) => {
        onRemoveSpy?.(resource);
        setValue((prev) => {
          const copy = { ...prev };
          delete copy[resource];
          return copy;
        });
      }}
      onAddResource={() => {}}
    />
  );
}

describe("PermissionToggleGrid", () => {
  it("renders exactly the catalog-allow-listed verbs per resource (D-02)", () => {
    render(<Grid />);

    // 4 verbs for ROLES + 2 for LEGAL_DOCUMENTS.
    expect(screen.getAllByRole("checkbox")).toHaveLength(6);

    // The 2-verb resource renders exactly its 2 checkboxes...
    expect(
      screen.getByRole("checkbox", { name: "View on Legal library" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", { name: "Download on Legal library" })
    ).toBeInTheDocument();

    // ...and no checkbox for a verb it does not support.
    expect(
      screen.queryByRole("checkbox", { name: "Remove on Legal library" })
    ).toBeNull();
    expect(
      screen.queryByRole("checkbox", { name: "Edit on Legal library" })
    ).toBeNull();
  });

  it("never renders a raw enum token as visible text (D-03)", () => {
    const { container } = render(<Grid />);

    expect(container).not.toHaveTextContent("LEGAL_DOCUMENTS");
    expect(container).not.toHaveTextContent("ROLES");
    expect(screen.getAllByText("Access management").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Legal library").length).toBeGreaterThan(0);
  });

  it("a newly added resource (empty verb array) renders all checkboxes unchecked", () => {
    render(<Grid initial={{ LEGAL_DOCUMENTS: [] }} />);

    const boxes = screen.getAllByRole("checkbox");
    expect(boxes).toHaveLength(2);
    for (const box of boxes) {
      expect(box).toHaveAttribute("data-state", "unchecked");
    }
  });

  it("every checkbox is reachable by its accessible name '{verb} on {resource}'", () => {
    render(<Grid />);

    // Every checkbox's accessible name is of the form "{verb} on {resource}".
    expect(screen.getAllByRole("checkbox", { name: /on / })).toHaveLength(6);
    expect(
      screen.getByRole("checkbox", { name: "Edit on Access management" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", { name: "Download on Legal library" })
    ).toBeInTheDocument();
  });

  it("exposes a single checkbox by its '{verb} on {resource}' name for a one-verb resource", () => {
    render(
      <PermissionToggleGrid
        catalog={[
          { resource: "ROLES", label: "Access management", verbs: [vb("READ", "View")] },
        ]}
        value={{ ROLES: [] }}
        onChange={vi.fn()}
        onRemoveResource={vi.fn()}
        onAddResource={vi.fn()}
      />
    );

    expect(
      screen.getByRole("checkbox", { name: /on / })
    ).toHaveAccessibleName("View on Access management");
  });

  it("toggling a verb calls onChange once with a NEW object (not the prev value)", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn<(next: GrantMap, prev: GrantMap) => void>();
    render(<Grid onChangeSpy={onChangeSpy} />);

    await user.click(
      screen.getByRole("checkbox", { name: "Add on Access management" })
    );

    expect(onChangeSpy).toHaveBeenCalledTimes(1);
    const [next, prev] = onChangeSpy.mock.calls[0];
    expect(next).not.toBe(prev); // new object (T-38-20)
    expect(next.ROLES).not.toBe(prev.ROLES); // new array for the affected resource
    expect(next.ROLES).toContain("CREATE");
  });

  it("unchecking removes only that verb and leaves the others intact", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn<(next: GrantMap, prev: GrantMap) => void>();
    render(
      <Grid
        initial={{ ROLES: ["READ", "UPDATE"], LEGAL_DOCUMENTS: [] }}
        onChangeSpy={onChangeSpy}
      />
    );

    await user.click(
      screen.getByRole("checkbox", { name: "View on Access management" })
    );

    const [next] = onChangeSpy.mock.calls[0];
    expect(next.ROLES).toEqual(["UPDATE"]);
  });

  it("the remove control fires onRemoveResource and opens no dialog", async () => {
    const user = userEvent.setup();
    const onRemoveSpy = vi.fn<(resource: string) => void>();
    render(<Grid onRemoveSpy={onRemoveSpy} />);

    await user.click(
      screen.getByRole("button", { name: "Remove Legal library" })
    );

    expect(onRemoveSpy).toHaveBeenCalledWith("LEGAL_DOCUMENTS");
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("disabled turns off every checkbox and remove control", () => {
    render(
      <PermissionToggleGrid
        catalog={CATALOG}
        value={{ ROLES: [] }}
        onChange={vi.fn()}
        onRemoveResource={vi.fn()}
        onAddResource={vi.fn()}
        disabled
      />
    );

    for (const box of screen.getAllByRole("checkbox")) {
      expect(box).toBeDisabled();
    }
    expect(
      screen.getByRole("button", { name: "Remove Access management" })
    ).toBeDisabled();
  });
});
