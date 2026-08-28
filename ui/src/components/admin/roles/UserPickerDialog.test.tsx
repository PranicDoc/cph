import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { UserPickerDialog } from "./UserPickerDialog";
import type { AdminHealer } from "@/hooks/admin/useAdminHealers";

function healer(overrides: Partial<AdminHealer> & { id: string }): AdminHealer {
  return {
    id: overrides.id,
    fullName: overrides.fullName ?? "",
    email: overrides.email ?? `${overrides.id}@corp.com`,
    phone: null,
    role: overrides.role ?? "HEALER",
    healingLevel: null,
    active: true,
    createdAt: "2026-01-01T00:00:00Z",
    hasPassword: true,
    hasGoogleLinked: false,
  };
}

// One SUPADM, one already-assigned healer, two assignable (one name-less).
const HEALERS: AdminHealer[] = [
  healer({ id: "h-sup", fullName: "Sam Super", email: "sam@root.com", role: "SUPADM" }),
  healer({ id: "u1", fullName: "Alice Assigned", email: "alice@corp.com" }),
  healer({ id: "h-bob", fullName: "Bob Builder", email: "bob@corp.com" }),
  healer({ id: "h-zoe", fullName: "", email: "zoe@corp.com" }),
];

function Harness({
  onConfirmSpy,
  onOpenChangeSpy,
}: {
  onConfirmSpy: (userIds: string[]) => void;
  onOpenChangeSpy?: (open: boolean) => void;
}) {
  const [open, setOpen] = useState(true);
  return (
    <>
      <button onClick={() => setOpen(true)}>reopen</button>
      <UserPickerDialog
        open={open}
        onOpenChange={(o) => {
          onOpenChangeSpy?.(o);
          setOpen(o);
        }}
        healers={HEALERS}
        alreadyAssigned={["u1"]}
        onConfirm={onConfirmSpy}
      />
    </>
  );
}

describe("UserPickerDialog", () => {
  it("excludes SUPADM accounts and already-assigned users, offers the rest", () => {
    render(<Harness onConfirmSpy={vi.fn()} />);

    // SUPADM never offered.
    expect(screen.queryByText("Sam Super")).toBeNull();
    // Already-assigned healer never offered.
    expect(screen.queryByText("Alice Assigned")).toBeNull();
    // Both assignable healers present; the name-less one shows by email.
    expect(screen.getByText("Bob Builder")).toBeInTheDocument();
    expect(screen.getByText("zoe@corp.com")).toBeInTheDocument();
  });

  it("renders the SUPADM exclusion note", () => {
    render(<Harness onConfirmSpy={vi.fn()} />);
    expect(
      screen.getByText("Super admins already have full access and aren't listed."),
    ).toBeInTheDocument();
  });

  it("filters by an email fragment, not only by name", async () => {
    const user = userEvent.setup();
    render(<Harness onConfirmSpy={vi.fn()} />);

    await user.type(
      screen.getByPlaceholderText("Search by name or email"),
      "zoe@",
    );

    expect(screen.getByText("zoe@corp.com")).toBeInTheDocument();
    expect(screen.queryByText("Bob Builder")).toBeNull();
  });

  it("keeps the dialog open when an option is selected", async () => {
    const user = userEvent.setup();
    render(<Harness onConfirmSpy={vi.fn()} />);

    await user.click(screen.getByText("Bob Builder"));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Assign users" })).toBeEnabled();
  });

  it("confirm calls onConfirm with an array of healer ids, then closes", async () => {
    const user = userEvent.setup();
    const onConfirmSpy = vi.fn<(userIds: string[]) => void>();
    const onOpenChangeSpy = vi.fn<(open: boolean) => void>();
    render(
      <Harness onConfirmSpy={onConfirmSpy} onOpenChangeSpy={onOpenChangeSpy} />,
    );

    await user.click(screen.getByText("Bob Builder"));
    await user.click(screen.getByText("zoe@corp.com"));
    await user.click(screen.getByRole("button", { name: "Assign users" }));

    expect(onConfirmSpy).toHaveBeenCalledTimes(1);
    const [ids] = onConfirmSpy.mock.calls[0];
    expect(ids).toEqual(expect.arrayContaining(["h-bob", "h-zoe"]));
    expect(ids.every((id) => typeof id === "string")).toBe(true);
    expect(onOpenChangeSpy).toHaveBeenLastCalledWith(false);
  });

  it("dismiss never calls onConfirm and reopening starts with an empty selection", async () => {
    const user = userEvent.setup();
    const onConfirmSpy = vi.fn();
    render(<Harness onConfirmSpy={onConfirmSpy} />);

    await user.click(screen.getByText("Bob Builder"));
    expect(screen.getByRole("button", { name: "Assign users" })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "Keep current users" }));
    expect(onConfirmSpy).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "reopen" }));
    expect(screen.getByRole("button", { name: "Assign users" })).toBeDisabled();
  });

  it("confirm is disabled with nothing selected", () => {
    render(<Harness onConfirmSpy={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Assign users" })).toBeDisabled();
  });
});
