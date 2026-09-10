import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { PermissionToggleGrid, type GrantMap } from "./PermissionToggleGrid";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

const vb = (value: string, label: string, locked = false) => ({
  value,
  label,
  description: `${label} action`,
  locked,
});

// "Access management" sorts before "Legal library", so ROLES is the resource
// the grid selects by default. ROLES:UPDATE is locked. Labels share no
// substring with their raw enum tokens.
const CATALOG: RbacCatalogResource[] = [
  {
    resource: "ROLES",
    label: "Access management",
    verbs: [
      vb("READ", "View"),
      vb("CREATE", "Add"),
      vb("UPDATE", "Edit", true),
      vb("DELETE", "Remove"),
    ],
  },
  {
    resource: "LEGAL_DOCUMENTS",
    label: "Legal library",
    verbs: [vb("READ", "View"), vb("EXPORT", "Download")],
  },
];

function Grid({
  initial = { ROLES: [], LEGAL_DOCUMENTS: [] },
  baseline,
  onChangeSpy,
  onRemoveSpy,
  onAddSpy,
  disabled,
}: {
  initial?: GrantMap;
  baseline?: GrantMap;
  onChangeSpy?: (next: GrantMap, prev: GrantMap) => void;
  onRemoveSpy?: (resource: string) => void;
  onAddSpy?: () => void;
  disabled?: boolean;
}) {
  const [value, setValue] = useState<GrantMap>(initial);
  return (
    <PermissionToggleGrid
      catalog={CATALOG}
      value={value}
      baseline={baseline}
      disabled={disabled}
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
      onAddResource={() => onAddSpy?.()}
    />
  );
}

describe("PermissionToggleGrid", () => {
  it("shows only the selected resource's verbs, as switches named '{verb} on {resource}'", () => {
    render(<Grid />);

    // Default selection is ROLES ("Access management") — its 4 verbs.
    expect(screen.getAllByRole("switch")).toHaveLength(4);
    expect(
      screen.getByRole("switch", { name: "Edit on Access management" }),
    ).toBeInTheDocument();
    // A verb from the *unselected* resource is not in the DOM yet.
    expect(
      screen.queryByRole("switch", { name: "Download on Legal library" }),
    ).toBeNull();
  });

  it("selecting a resource in the left panel swaps the right panel to its verbs", async () => {
    const user = userEvent.setup();
    render(<Grid />);

    await user.click(screen.getByRole("button", { name: /^Legal library/ }));

    expect(screen.getAllByRole("switch")).toHaveLength(2);
    expect(
      screen.getByRole("switch", { name: "Download on Legal library" }),
    ).toBeInTheDocument();
  });

  it("lists every added resource with a '{granted}/{total} granted' subtitle", () => {
    render(<Grid initial={{ ROLES: ["READ"], LEGAL_DOCUMENTS: [] }} />);

    // ROLES: READ granted + UPDATE always-on (locked) = 2 of 4.
    expect(screen.getByText("2/4 granted")).toBeInTheDocument();
    expect(screen.getByText("0/2 granted")).toBeInTheDocument();
  });

  it("never renders a raw enum token as visible text (D-03)", () => {
    const { container } = render(<Grid />);
    expect(container).not.toHaveTextContent("LEGAL_DOCUMENTS");
    expect(container).not.toHaveTextContent("ROLES");
    expect(screen.getAllByText("Access management").length).toBeGreaterThan(0);
  });

  it("toggling a verb calls onChange once with a NEW object and NEW array", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn<(next: GrantMap, prev: GrantMap) => void>();
    render(<Grid onChangeSpy={onChangeSpy} />);

    await user.click(
      screen.getByRole("switch", { name: "Add on Access management" }),
    );

    expect(onChangeSpy).toHaveBeenCalledTimes(1);
    const [next, prev] = onChangeSpy.mock.calls[0];
    expect(next).not.toBe(prev);
    expect(next.ROLES).not.toBe(prev.ROLES);
    expect(next.ROLES).toContain("CREATE");
  });

  it("unchecking removes only that verb", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn<(next: GrantMap, prev: GrantMap) => void>();
    render(
      <Grid
        initial={{ ROLES: ["READ", "DELETE"], LEGAL_DOCUMENTS: [] }}
        onChangeSpy={onChangeSpy}
      />,
    );

    await user.click(
      screen.getByRole("switch", { name: "View on Access management" }),
    );

    const [next] = onChangeSpy.mock.calls[0];
    expect(next.ROLES).toEqual(["DELETE"]);
  });

  it("a locked verb renders on + disabled and cannot be toggled", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(<Grid onChangeSpy={onChangeSpy} />);

    const locked = screen.getByRole("switch", { name: "Edit on Access management" });
    expect(locked).toBeDisabled();
    expect(locked).toHaveAttribute("data-state", "checked");

    await user.click(locked);
    expect(onChangeSpy).not.toHaveBeenCalled();
  });

  it("baseline drives a CHANGED badge on verbs whose state differs from the saved role", () => {
    // Saved: ROLES had READ. Current: READ removed, CREATE added.
    render(
      <Grid
        initial={{ ROLES: ["CREATE"], LEGAL_DOCUMENTS: [] }}
        baseline={{ ROLES: ["READ"], LEGAL_DOCUMENTS: [] }}
      />,
    );

    // Two verbs changed → two CHANGED badges on the selected resource.
    expect(screen.getAllByText("Changed")).toHaveLength(2);
  });

  it("the remove control fires onRemoveResource and opens no dialog", async () => {
    const user = userEvent.setup();
    const onRemoveSpy = vi.fn<(resource: string) => void>();
    render(<Grid onRemoveSpy={onRemoveSpy} />);

    await user.click(
      screen.getByRole("button", { name: "Remove Legal library" }),
    );

    expect(onRemoveSpy).toHaveBeenCalledWith("LEGAL_DOCUMENTS");
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("the '+ Add resource' control fires onAddResource", async () => {
    const user = userEvent.setup();
    const onAddSpy = vi.fn();
    render(<Grid onAddSpy={onAddSpy} />);

    await user.click(screen.getByRole("button", { name: /Add resource/ }));
    expect(onAddSpy).toHaveBeenCalledTimes(1);
  });

  it("the empty state offers 'Add resource' and renders no switches", () => {
    render(<Grid initial={{}} onAddSpy={vi.fn()} />);

    expect(screen.getByText("No resources added yet")).toBeInTheDocument();
    expect(screen.queryAllByRole("switch")).toHaveLength(0);
    expect(
      screen.getByRole("button", { name: /Add resource/ }),
    ).toBeInTheDocument();
  });

  it("disabled turns off every switch, the remove control and the add control", () => {
    render(<Grid disabled />);

    for (const sw of screen.getAllByRole("switch")) {
      expect(sw).toBeDisabled();
    }
    expect(
      screen.getByRole("button", { name: "Remove Access management" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: /Add resource/ }),
    ).toBeDisabled();
  });
});
