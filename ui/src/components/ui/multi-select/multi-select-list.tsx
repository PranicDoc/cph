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
      <CommandInput placeholder={searchPlaceholder} disabled={disabled} />

      <CommandList>
        <CommandEmpty>{emptyText}</CommandEmpty>

        <CommandGroup>
          {options.map((option) => {
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
