import { useEffect, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { MultiSelect } from "./multi-select";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";

const OPTIONS = ["Red", "Green", "Blue"];

function ControlledMultiSelect({
  onChangeSpy,
}: {
  onChangeSpy: (value: string[]) => void;
}) {
  const [value, setValue] = useState<string[]>([]);
  return (
    <MultiSelect<string>
      options={OPTIONS}
      value={value}
      onChange={(next) => {
        setValue(next);
        onChangeSpy(next);
      }}
      getOptionKey={(option) => option}
      getOptionLabel={(option) => option}
      placeholder="Select colors"
    />
  );
}

/**
 * Mirrors ColorPropertySelector, which computes `selectedColors =
 * value.map(x => x.color)` fresh on every render -- a brand new array
 * reference even when the content hasn't changed. `unrelatedTick` forces a
 * parent re-render (e.g. an unrelated sibling row's data changing, a
 * reference-data fetch resolving) with no actual change to the committed
 * selection, to check that alone doesn't corrupt an already-live-committed
 * selection.
 */
function ControlledMultiSelectWithUnstableValueProp({
  onChangeSpy,
  unrelatedTick,
}: {
  onChangeSpy: (value: string[]) => void;
  unrelatedTick: number;
}) {
  const [value, setValue] = useState<string[]>([]);
  const derivedValue = value.map((v) => v);
  void unrelatedTick;
  return (
    <MultiSelect<string>
      options={OPTIONS}
      value={derivedValue}
      onChange={(next) => {
        setValue(next);
        onChangeSpy(next);
      }}
      getOptionKey={(option) => option}
      getOptionLabel={(option) => option}
      placeholder="Select colors"
    />
  );
}

/**
 * The real usage (ClosureForm inside Closure.tsx's Dialog) nests MultiSelect
 * inside a modal Dialog -- that's exactly the composition that broke Popover
 * originally, so the regression test must replicate it, not test MultiSelect
 * in isolation.
 */
function renderInsideModalDialog(onChangeSpy: (value: string[]) => void) {
  return render(
    <Dialog open>
      <DialogContent>
        <DialogTitle>Test Dialog</DialogTitle>
        <ControlledMultiSelect onChangeSpy={onChangeSpy} />
      </DialogContent>
    </Dialog>
  );
}

/**
 * Text can legitimately match twice once a selection is live-committed:
 * once as a badge on the trigger, once as the dropdown list item. Scope
 * specifically to the list item (inside a "command-item"), not the badge.
 */
function getListItem(itemText: string) {
  const matches = screen.getAllByText(itemText);
  const listItem = matches
    .map((el) => el.closest('[data-slot="command-item"]'))
    .find((el): el is HTMLElement => el !== null);
  if (!listItem) throw new Error(`command item for "${itemText}" not found`);
  return listItem;
}

function getItemCheckbox(itemText: string) {
  return getListItem(itemText).querySelector('[data-slot="checkbox"]');
}

describe("MultiSelect nested inside a modal Dialog", () => {
  it("opens the dropdown list when the trigger is clicked", async () => {
    const user = userEvent.setup();
    renderInsideModalDialog(vi.fn());

    await user.click(screen.getByRole("combobox"));

    expect(await screen.findByText("Red")).toBeInTheDocument();
    expect(screen.getByText("Green")).toBeInTheDocument();
    expect(screen.getByText("Blue")).toBeInTheDocument();
  });

  it("portals the dropdown panel inside DialogContent, not document.body, so the modal's scroll-lock doesn't block scrolling the option list", async () => {
    const user = userEvent.setup();
    renderInsideModalDialog(vi.fn());

    await user.click(screen.getByRole("combobox"));
    await screen.findByText("Red");

    // Regression guard: previously the panel was portaled straight to
    // document.body as a sibling of the Dialog's own portal root, which
    // react-remove-scroll's modal scroll-lock then treated as "outside"
    // and silently blocked from scrolling entirely, even though it was
    // visibly rendered. Being a real DOM descendant of DialogContent
    // makes the lock treat it like any other in-dialog scrollable region.
    const dialogContent = document.querySelector('[data-slot="dialog-content"]');
    const list = screen.getByText("Red").closest('[data-slot="command-list"]');
    expect(dialogContent).toContainElement(list as HTMLElement);
  });

  it("selecting an item checks it, keeps the dropdown open, AND commits immediately to the parent", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));

    // Regression guard: the dropdown must NOT close after a single item
    // click (that was the portal/bubbling bug) -- Done/Cancel still present.
    expect(screen.getByRole("button", { name: "Done" })).toBeInTheDocument();
    expect(getItemCheckbox("Red")).toHaveAttribute("data-state", "checked");
    expect(getItemCheckbox("Green")).toHaveAttribute("data-state", "unchecked");

    // ROOT CAUSE regression guard: the tick must not be a purely local,
    // unsaved draft -- it must reach the parent's onChange right away,
    // without requiring a separate "Done" click.
    expect(onChangeSpy).toHaveBeenCalledWith(["Red"]);
  });

  it("checking an item and moving on WITHOUT clicking Done still reaches the parent (no silent data loss)", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));
    // Deliberately do NOT click Done -- simulate the user moving on to a
    // different row/field, exactly as reported.
    await user.keyboard("{Escape}");

    expect(onChangeSpy).toHaveBeenCalledWith(["Red"]);
  });

  it("clicking an already-checked item unchecks it, without closing, and commits the removal immediately", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));
    expect(getItemCheckbox("Red")).toHaveAttribute("data-state", "checked");

    await user.click(getListItem("Red"));
    expect(getItemCheckbox("Red")).toHaveAttribute("data-state", "unchecked");

    expect(onChangeSpy).toHaveBeenNthCalledWith(1, ["Red"]);
    expect(onChangeSpy).toHaveBeenNthCalledWith(2, []);
  });

  it("selecting multiple items commits each one incrementally and closes on Done", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByText("Blue"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect(onChangeSpy).toHaveBeenNthCalledWith(1, ["Red"]);
    expect(onChangeSpy).toHaveBeenNthCalledWith(2, ["Red", "Blue"]);
    expect(screen.queryByRole("button", { name: "Done" })).not.toBeInTheDocument();
  });

  it("Cancel reverts to the value the dropdown had when it was opened", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    // Commit "Red" first, as a prior, already-saved selection.
    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByRole("button", { name: "Done" }));
    onChangeSpy.mockClear();

    // Reopen, add "Blue" (live-committed), then Cancel -- should revert
    // back to just ["Red"], discarding the "Blue" addition.
    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Blue"));
    expect(onChangeSpy).toHaveBeenLastCalledWith(["Red", "Blue"]);

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onChangeSpy).toHaveBeenLastCalledWith(["Red"]);

    await user.click(screen.getByRole("combobox"));
    expect(getItemCheckbox("Red")).toHaveAttribute("data-state", "checked");
    expect(getItemCheckbox("Blue")).toHaveAttribute("data-state", "unchecked");
  });

  it("an unrelated parent re-render (new value array, same content) does not corrupt an already-committed selection", async () => {
    const user = userEvent.setup();

    let triggerRerender = () => {};

    function Harness() {
      const [tick, setTick] = useState(0);
      useEffect(() => {
        triggerRerender = () => setTick((t) => t + 1);
      }, []);
      return (
        <Dialog open>
          <DialogContent>
            <DialogTitle>Test Dialog</DialogTitle>
            <ControlledMultiSelectWithUnstableValueProp
              onChangeSpy={vi.fn()}
              unrelatedTick={tick}
            />
          </DialogContent>
        </Dialog>
      );
    }

    render(<Harness />);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));
    expect(getItemCheckbox("Red")).toHaveAttribute("data-state", "checked");

    // Force an unrelated re-render of the parent (e.g. an unrelated sibling
    // row's data changing, a background query resolving) -- not a real
    // outside click, while the dropdown is still open.
    await act(async () => {
      triggerRerender();
    });

    expect(screen.getByRole("button", { name: "Done" })).toBeInTheDocument();
    expect(getItemCheckbox("Red")).toHaveAttribute("data-state", "checked");
  });

  it("disabled prevents opening the dropdown and toggling items", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Test Dialog</DialogTitle>
          <MultiSelect<string>
            options={OPTIONS}
            value={[]}
            onChange={onChangeSpy}
            getOptionKey={(option) => option}
            getOptionLabel={(option) => option}
            placeholder="Select colors"
            disabled
          />
        </DialogContent>
      </Dialog>
    );

    await user.click(screen.getByRole("combobox"));
    expect(screen.queryByText("Red")).not.toBeInTheDocument();
    expect(onChangeSpy).not.toHaveBeenCalled();
  });

  it("renders selected badges on the trigger and a +N overflow badge past maxBadges", async () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Test Dialog</DialogTitle>
          <MultiSelect<string>
            options={OPTIONS}
            value={["Red", "Green", "Blue"]}
            onChange={vi.fn()}
            getOptionKey={(option) => option}
            getOptionLabel={(option) => option}
            placeholder="Select colors"
            maxBadges={2}
          />
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText("Red")).toBeInTheDocument();
    expect(screen.getByText("Green")).toBeInTheDocument();
    expect(screen.getByText("+1")).toBeInTheDocument();
  });

  it("uses renderOption and renderBadge overrides when provided", async () => {
    const user = userEvent.setup();
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Test Dialog</DialogTitle>
          <MultiSelect<string>
            options={OPTIONS}
            value={["Red"]}
            onChange={vi.fn()}
            getOptionKey={(option) => option}
            getOptionLabel={(option) => option}
            placeholder="Select colors"
            renderBadge={(option) => `Badge:${option}`}
            renderOption={(option, selected) => `Option:${option}:${selected}`}
          />
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText("Badge:Red")).toBeInTheDocument();

    await user.click(screen.getByRole("combobox"));
    expect(await screen.findByText("Option:Red:true")).toBeInTheDocument();
    expect(screen.getByText("Option:Green:false")).toBeInTheDocument();
  });

  it("clicking truly outside the panel closes it without discarding already-committed selections", async () => {
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    renderInsideModalDialog(onChangeSpy);

    await user.click(screen.getByRole("combobox"));
    await user.click(await screen.findByText("Red"));

    // Click the Dialog title -- genuinely outside the trigger/panel, but
    // still inside the modal Dialog's own DOM subtree.
    await user.click(screen.getByText("Test Dialog"));

    expect(screen.queryByRole("button", { name: "Done" })).not.toBeInTheDocument();
    expect(onChangeSpy).toHaveBeenCalledWith(["Red"]);
  });

  it("shows the placeholder when nothing is selected, and a loading spinner while loading", () => {
    const { rerender } = render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Test Dialog</DialogTitle>
          <MultiSelect<string>
            options={OPTIONS}
            value={[]}
            onChange={vi.fn()}
            getOptionKey={(option) => option}
            getOptionLabel={(option) => option}
            placeholder="Select colors"
          />
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText("Select colors")).toBeInTheDocument();

    rerender(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Test Dialog</DialogTitle>
          <MultiSelect<string>
            options={OPTIONS}
            value={[]}
            onChange={vi.fn()}
            getOptionKey={(option) => option}
            getOptionLabel={(option) => option}
            placeholder="Select colors"
            loading
          />
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByRole("combobox").querySelector("svg.lucide-loader-circle")).toBeInTheDocument();
  });
});
