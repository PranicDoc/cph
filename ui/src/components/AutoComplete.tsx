import { useState, useRef, useEffect, KeyboardEvent } from "react";

interface AutocompleteProps<T> {
  options: T[];
  getOptionLabel: (option: T) => string;
  onSelect: (option: T) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}

export function Autocomplete<T>({
  options,
  getOptionLabel,
  onSelect,
  placeholder = "Search...",
  className = "",
  disabled = false,
}: AutocompleteProps<T>) {
  const [query, setQuery] = useState("");
  const [isOpen, setIsOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const filteredOptions =
    query.trim() === ""
      ? options
      : options.filter((opt) =>
          getOptionLabel(opt).toLowerCase().includes(query.toLowerCase())
        );

  const handleSelect = (option: T) => {
    onSelect(option);
    setQuery("");
    setIsOpen(false);
    setHighlightedIndex(0);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!isOpen) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        setIsOpen(true);
        setHighlightedIndex(0);
        e.preventDefault();
      }
      return;
    }

    switch (e.key) {
      case "ArrowDown":
        setHighlightedIndex((prev) => (prev + 1) % filteredOptions.length);
        e.preventDefault();
        break;
      case "ArrowUp":
        setHighlightedIndex(
          (prev) => (prev - 1 + filteredOptions.length) % filteredOptions.length
        );
        e.preventDefault();
        break;
      case "Enter":
        if (filteredOptions[highlightedIndex]) {
          handleSelect(filteredOptions[highlightedIndex]);
        }
        e.preventDefault();
        break;
      case "Escape":
        setIsOpen(false);
        break;
    }
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        wrapperRef.current &&
        !wrapperRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div ref={wrapperRef} className={`relative ${className}`}>
      <input
        ref={inputRef}
        type="text"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setIsOpen(true);
          setHighlightedIndex(0);
        }}
        onFocus={() => setIsOpen(true)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled}
        className="w-full text-sm p-2 border border-border rounded focus:outline-none focus:ring-2 focus:ring-primary"
      />
      {isOpen && filteredOptions.length > 0 && (
        <ul className="absolute z-10 mt-1 w-full bg-white border border-border rounded-lg shadow-lg max-h-60 overflow-auto">
          {filteredOptions.map((option, idx) => (
            <li
              key={getOptionLabel(option)}
              className={`
                px-3 py-2 cursor-pointer text-sm
                ${
                  idx === highlightedIndex
                    ? "bg-primary/10"
                    : "hover:bg-gray-50"
                }
              `}
              onClick={() => handleSelect(option)}
              onMouseEnter={() => setHighlightedIndex(idx)}
            >
              {getOptionLabel(option)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
