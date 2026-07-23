import { useState } from "react"
import {
  Command,
  CommandEmpty,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import { MultiSelectOverlay } from "@/components/ui/multi-select/multi-select-overlay"
import { cn } from "@/lib/utils"

interface ComboboxProps<T> {
  options: T[]
  getOptionLabel: (option: T) => string
  onSelect: (option: T) => void
  placeholder?: string
  className?: string
  disabled?: boolean
}

/**
 * Inline search-select on cmdk Command, with its results list portaled out
 * via MultiSelectOverlay instead of rendered inline. An inline-positioned
 * dropdown gets clipped/forces a scroll when the Combobox sits inside a
 * narrow scrolling container (e.g. a table cell) — MultiSelectOverlay
 * already solves exactly this (see its own comment for why Radix Popover
 * was rejected here). CommandInput stays rendered where the caller put it;
 * only CommandList relocates, which is safe because portals preserve React
 * context (cmdk's Command root) regardless of DOM position.
 */
export function Combobox<T>({
  options,
  getOptionLabel,
  onSelect,
  placeholder = "Search...",
  className,
  disabled = false,
}: ComboboxProps<T>) {
  const [query, setQuery] = useState("")
  const [open, setOpen] = useState(false)

  const handleSelect = (option: T) => {
    onSelect(option)
    setQuery("")
    setOpen(false)
  }

  return (
    <Command className={cn("overflow-visible rounded-lg! bg-transparent p-0", className)}>
      <MultiSelectOverlay
        open={open}
        onOpenChange={setOpen}
        toggleOnTriggerClick={false}
        trigger={
          <CommandInput
            value={query}
            onValueChange={(v) => {
              setQuery(v)
              setOpen(true)
            }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder}
            disabled={disabled}
          />
        }
      >
        <CommandList className="max-h-60">
          <CommandEmpty className="py-3 text-sm text-text-light">
            No matches found
          </CommandEmpty>
          {options.map((option) => {
            const label = getOptionLabel(option)
            return (
              <CommandItem
                key={label}
                value={label}
                onSelect={() => handleSelect(option)}
              >
                {label}
              </CommandItem>
            )
          })}
        </CommandList>
      </MultiSelectOverlay>
    </Command>
  )
}
