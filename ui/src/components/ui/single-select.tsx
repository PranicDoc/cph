import { useState } from "react";
import { Check, ChevronDown } from "lucide-react";

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { MultiSelectOverlay } from "@/components/ui/multi-select/multi-select-overlay";
import { cn } from "@/lib/utils";

export interface SingleSelectOption {
  value: string;
  label: string;
}

interface SingleSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SingleSelectOption[];
  placeholder?: string;
  disabled?: boolean;
  id?: string;
  "aria-label"?: string;
  /** Classes for the trigger button; defaults to the app's outline field look. */
  className?: string;
  /** Show the search box. Defaults to on when there are more than 7 options. */
  searchable?: boolean;
}

const DEFAULT_TRIGGER =
  "flex h-10 w-full items-center justify-between gap-2 rounded-md border border-border bg-background px-3 text-sm text-foreground";

/**
 * The app's single-select dropdown. It opens the same popover panel as the multi-selects (e.g. the
 * chakra picker): popover background and text colours, border, shadow, a searchable list. Use it in
 * place of a native <select>, whose option list the browser draws on a white background and which
 * is unreadable next to light text (dark theme).
 */
export function SingleSelect({
  value,
  onChange,
  options,
  placeholder = "Select...",
  disabled,
  id,
  className,
  searchable,
  ...rest
}: SingleSelectProps) {
  const [open, setOpen] = useState(false);
  const selected = options.find((o) => o.value === value);
  const showSearch = searchable ?? options.length > 7;

  return (
    <MultiSelectOverlay
      open={open && !disabled}
      onOpenChange={setOpen}
      trigger={
        <button
          type="button"
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-label={rest["aria-label"]}
          disabled={disabled}
          className={cn(
            className ?? DEFAULT_TRIGGER,
            "text-left disabled:cursor-not-allowed disabled:opacity-60"
          )}
        >
          <span className={cn("min-w-0 flex-1 truncate", !selected && "opacity-60")}>
            {selected ? selected.label : placeholder}
          </span>
          <ChevronDown className="h-4 w-4 shrink-0 opacity-60" aria-hidden="true" />
        </button>
      }
    >
      <Command>
        {showSearch && <CommandInput placeholder="Search..." />}
        <CommandList>
          <CommandEmpty>No results found.</CommandEmpty>
          <CommandGroup>
            {options.map((option) => (
              <CommandItem
                key={option.value}
                value={option.label}
                onSelect={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
                className="gap-2"
              >
                <Check
                  className={cn("h-4 w-4 shrink-0", option.value === value ? "opacity-100" : "opacity-0")}
                  aria-hidden="true"
                />
                <span>{option.label}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </Command>
    </MultiSelectOverlay>
  );
}
