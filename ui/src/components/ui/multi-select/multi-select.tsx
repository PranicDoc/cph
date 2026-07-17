import { useEffect, useState } from "react";

import { MultiSelectList } from "./multi-select-list";
import { MultiSelectOverlay } from "./multi-select-overlay";
import { MultiSelectTrigger } from "./multi-select-trigger";
import { MultiSelectProps } from "./types";

import { Button } from "@/components/ui/button";

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

  /**
   * Temporary selection while popup is open.
   */
  const [draftValue, setDraftValue] = useState<T[]>(value);

  /**
   * Whenever parent changes,
   * keep local draft in sync.
   */
  useEffect(() => {
    setDraftValue(value);
  }, [value]);

  /**
   * User clicked Cancel.
   */
  const handleCancel = () => {
    setDraftValue(value);
    setOpen(false);
  };

  /**
   * User clicked Done.
   */
  const handleDone = () => {
    onChange(draftValue);
    setOpen(false);
  };

  useEffect(() => {
    console.log("MultiSelect MOUNTED");
    return () => console.log("MultiSelect UNMOUNTED");
  }, []);

  return (
    <MultiSelectOverlay
      open={open}
      onOpenChange={(nextOpen) => {
        console.log("onOpenChange", nextOpen, new Error().stack);
        if (nextOpen) {
          setDraftValue(value);
        }

        setOpen(nextOpen);
      }}
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
          value={draftValue}
          onChange={setDraftValue}
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
