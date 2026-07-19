import { useCallback, useRef, useState } from "react";

import { MultiSelectList } from "./multi-select-list";
import { MultiSelectOverlay } from "./multi-select-overlay";
import { MultiSelectTrigger } from "./multi-select-trigger";
import { MultiSelectProps } from "./types";

import { Button } from "@/components/ui/button";

/**
 * Selections commit to the parent immediately on every toggle -- there is
 * no staged "draft" the user must remember to confirm. An earlier design
 * held toggles in local draft state and only called `onChange` from a
 * "Done" button; checking an item ticked the checkbox but never reached
 * the parent unless the user explicitly clicked Done, so moving on (e.g.
 * clicking a different row, or "Next") silently discarded the selection.
 *
 * `openSnapshotRef` captures `value` at the moment the dropdown opens, so
 * "Cancel" can still revert to it -- that's its only job now.
 */
export function MultiSelect<T>({
  options,
  value,
  onChange,
  getOptionKey,
  getOptionLabel,
  renderOption,
  renderBadge,
  placeholder = "Select...",
  searchPlaceholder = "Search...",
  emptyText = "No results found.",
  className,
  disabled = false,
  maxBadges = 2,
  title = "Select",
  loading,
}: MultiSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const openSnapshotRef = useRef<T[]>(value);

  /**
   * User clicked Cancel: revert to whatever the value was when the
   * dropdown opened.
   */
  const handleCancel = () => {
    onChange(openSnapshotRef.current);
    setOpen(false);
  };

  /**
   * User clicked Done: nothing to commit, every toggle already did.
   */
  const handleDone = () => {
    setOpen(false);
  };

  const handleOnOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (nextOpen) {
        openSnapshotRef.current = value;
      }

      setOpen(nextOpen);
    },
    [value]
  );

  return (
    <MultiSelectOverlay
      open={open}
      onOpenChange={handleOnOpenChange}
      title={title}
      trigger={
        <MultiSelectTrigger
          value={value}
          getOptionKey={getOptionKey}
          getOptionLabel={getOptionLabel}
          renderBadge={renderBadge}
          placeholder={placeholder}
          maxBadges={maxBadges}
          className={className}
          disabled={disabled}
          loading={loading}
        />
      }
    >
      <div className="flex flex-col">
        <MultiSelectList
          options={options}
          value={value}
          onChange={onChange}
          getOptionKey={getOptionKey}
          getOptionLabel={getOptionLabel}
          renderOption={renderOption}
          searchPlaceholder={searchPlaceholder}
          emptyText={emptyText}
          disabled={loading}
        />

        <div className="flex justify-between border-t p-3">
          <Button variant="ghost" onClick={handleCancel} disabled={loading}>
            Cancel
          </Button>

          <Button onClick={handleDone}>Done</Button>
        </div>
      </div>
    </MultiSelectOverlay>
  );
}
