import { useMemo, useRef, useState } from "react";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Checkbox } from "@/components/ui/checkbox";
import { MultiSelectListProps } from "./types";

export function MultiSelectList<T>({
  options,
  value,
  onChange,
  getOptionKey,
  getOptionLabel,
  renderOption,
  searchPlaceholder = "Search...",
  emptyText = "No results found.",
  disabled,
}: MultiSelectListProps<T>) {
  const listRef = useRef<HTMLDivElement>(null);

  // Selected-first order is fixed from the value when the list mounts (i.e. opens),
  // so rows don't jump under the pointer while the user ticks items.
  const [openValue] = useState(value);
  const ordered = useMemo(() => {
    const keys = new Set(openValue.map(getOptionKey));
    return [...options.filter((o) => keys.has(getOptionKey(o))), ...options.filter((o) => !keys.has(getOptionKey(o)))];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [options, openValue]);

  const isSelected = (option: T) =>
    value.some((selected) => getOptionKey(selected) === getOptionKey(option));

  const toggleOption = (option: T) => {
    if (isSelected(option)) {
      onChange(
        value.filter(
          (selected) => getOptionKey(selected) !== getOptionKey(option)
        )
      );
    } else {
      onChange([...value, option]);
    }
  };

  return (
    <Command>
      <CommandInput
        placeholder={searchPlaceholder}
        disabled={disabled}
        onValueChange={() => {
          // cmdk re-filters in place and keeps the old scrollTop, which hides the matches
          if (listRef.current) listRef.current.scrollTop = 0;
        }}
      />

      <CommandList ref={listRef}>
        <CommandEmpty>{emptyText}</CommandEmpty>

        <CommandGroup>
          {ordered.map((option) => {
            const selected = isSelected(option);

            return (
              <CommandItem
                key={getOptionKey(option)}
                value={getOptionLabel(option)}
                onSelect={() => toggleOption(option)}
                className="gap-2"
                disabled={disabled}
              >
                <Checkbox checked={selected} className="pointer-events-none" />

                {renderOption ? (
                  renderOption(option, selected)
                ) : (
                  <span>{getOptionLabel(option)}</span>
                )}
              </CommandItem>
            );
          })}
        </CommandGroup>
      </CommandList>
    </Command>
  );
}
