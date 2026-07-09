import { useEffect, useRef, useState } from "react"
import {
  Command,
  CommandEmpty,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
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
 * Inline search-select on cmdk Command. Drop-in replacement for the old
 * hand-rolled Autocomplete: type to filter, arrows + enter to pick.
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
  const wrapperRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        wrapperRef.current &&
        !wrapperRef.current.contains(event.target as Node)
      ) {
        setOpen(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [])

  const handleSelect = (option: T) => {
    onSelect(option)
    setQuery("")
    setOpen(false)
  }

  return (
    <div ref={wrapperRef} className={cn("relative", className)}>
      <Command className="overflow-visible rounded-lg! bg-transparent p-0">
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
        {open && (
          <CommandList className="absolute top-full z-10 mt-1 w-full rounded-lg border border-border bg-white shadow-lg max-h-60">
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
        )}
      </Command>
    </div>
  )
}
