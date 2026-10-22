import { X, Search } from "lucide-react";
import { useEffect, useRef, useState, useCallback } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { useUiVersion } from "../ui-version";

interface TagInputProps {
  value: any[];
  onChange: (value: any[]) => void;
  suggestions?: string[];
  placeholder?: string;
  label?: string;
  valueKey?: string;
  displayKey?: string;
  displayFormatter?: (tag: any) => string;
  editableFields?: Record<string, { label: string; placeholder?: string }>;
  createNewTag?: (input: string) => any;
}

export function TagInput({
  value,
  onChange,
  suggestions = [],
  placeholder = "Type to add...",
  label,
  valueKey,
  displayKey,
  displayFormatter,
  editableFields,
  createNewTag,
}: TagInputProps) {
  const { isNewView } = useUiVersion();
  const [input, setInput] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [editingTagIdx, setEditingTagIdx] = useState<number | null>(null);
  const [editingValues, setEditingValues] = useState<Record<string, string>>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Close dropdown and popover on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setShowDropdown(false);
        setActiveIndex(-1);
        setEditingTagIdx(null);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const resolvedDisplayKey = displayKey || valueKey;

  // Used for logic (duplicate check, remove) — always uses displayKey
  const getLogicText = useCallback(
    (tag: any): string => {
      if (resolvedDisplayKey && typeof tag === "object" && tag !== null) {
        return tag[resolvedDisplayKey] ?? "";
      }
      return typeof tag === "string" ? tag : "";
    },
    [resolvedDisplayKey]
  );

  // Used for chip display — prefers displayFormatter when provided
  const getDisplayText = useCallback(
    (tag: any): string => {
      if (displayFormatter && typeof tag === "object" && tag !== null) {
        return displayFormatter(tag);
      }
      return getLogicText(tag);
    },
    [displayFormatter, getLogicText]
  );

  const filtered = suggestions.filter(
    (s) =>
      s.toLowerCase().includes(input.toLowerCase()) &&
      !value.some((v) => getLogicText(v).toLowerCase() === s.toLowerCase())
  );

  const addTag = useCallback(
    (item: string) => {
      const trimmed = item.trim();
      if (!trimmed) return;

      const alreadyExists = value.some(
        (v) => getLogicText(v).toLowerCase() === trimmed.toLowerCase()
      );
      if (alreadyExists) {
        setInput("");
        return;
      }

      let newTag: any;
      if (createNewTag) {
        newTag = createNewTag(trimmed);
      } else if (valueKey) {
        newTag = { [valueKey]: trimmed };
      } else {
        newTag = trimmed;
      }

      onChange([...value, newTag]);
      setInput("");
      setActiveIndex(-1);
      inputRef.current?.focus();
    },
    [value, onChange, valueKey, createNewTag, getLogicText]
  );

  const removeTag = (tagToRemove: any) => {
    onChange(
      value.filter((v) => getLogicText(v) !== getLogicText(tagToRemove))
    );
    inputRef.current?.focus();
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInput(e.target.value);
    setShowDropdown(true);
    setActiveIndex(-1);
  };

  const handleFocus = () => {
    setShowDropdown(true);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      if (activeIndex >= 0 && activeIndex < filtered.length) {
        addTag(filtered[activeIndex]);
      } else if (input.trim()) {
        addTag(input.trim());
      }
      return;
    }

    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (filtered.length === 0) return;
      setActiveIndex((prev) => (prev < filtered.length - 1 ? prev + 1 : 0));
      return;
    }

    if (e.key === "ArrowUp") {
      e.preventDefault();
      if (filtered.length === 0) return;
      setActiveIndex((prev) => (prev > 0 ? prev - 1 : filtered.length - 1));
      return;
    }

    if (e.key === "Escape") {
      setShowDropdown(false);
      setActiveIndex(-1);
      setEditingTagIdx(null);
      return;
    }
  };

  const openPopover = (idx: number, item: any) => {
    if (!editableFields) return;
    setEditingTagIdx(idx);
    setShowDropdown(false);
    setEditingValues(
      Object.fromEntries(
        Object.keys(editableFields).map((k) => [k, item[k] ?? ""])
      )
    );
  };

  const savePopover = () => {
    if (editingTagIdx === null) return;
    const updated = [...value];
    updated[editingTagIdx] = { ...updated[editingTagIdx], ...editingValues };
    onChange(updated);
    setEditingTagIdx(null);
  };

  const highlightMatch = (text: string, query: string) => {
    if (!query) return text;
    const regex = new RegExp(
      `(${query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`,
      "gi"
    );
    const parts = text.split(regex);
    return parts.map((part, i) =>
      part.toLowerCase() === query.toLowerCase() ? (
        <span
          key={i}
          className="text-primary font-semibold bg-primary/10 rounded px-0.5"
        >
          {part}
        </span>
      ) : (
        <span key={i}>{part}</span>
      )
    );
  };

  return (
    <div className="relative" ref={containerRef}>
      {label && (
        <Label className="block text-sm font-semibold text-text-light mb-2">
          {label}
        </Label>
      )}
      <div
        className={
          isNewView
            ? "input flex flex-wrap gap-2 items-center cursor-text"
            : "border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-all duration-200 bg-white cursor-text"
        }
        style={isNewView ? { height: "auto", minHeight: 44, padding: "6px 8px" } : undefined}
        onClick={() => inputRef.current?.focus()}
      >
        {value.map((item, idx) => {
          const display = getDisplayText(item);
          if (isNewView) {
            return (
              <span
                key={getLogicText(item) + idx}
                className="chip on"
                style={editableFields ? { cursor: "pointer" } : undefined}
                onClick={(e) => {
                  e.stopPropagation();
                  openPopover(idx, item);
                }}
              >
                {display}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    removeTag(item);
                  }}
                  aria-label={`Remove ${display}`}
                  style={{ border: 0, background: "transparent", padding: 0, color: "inherit", display: "inline-flex", cursor: "pointer" }}
                >
                  <X style={{ width: 13, height: 13 }} />
                </button>
              </span>
            );
          }
          return (
            <Badge
              key={getLogicText(item) + idx}
              className={cn(
                "gap-1.5 px-3 py-1 h-auto border-primary/20 hover:bg-primary/20 transition-colors",
                editableFields && "cursor-pointer"
              )}
              onClick={(e) => {
                e.stopPropagation();
                openPopover(idx, item);
              }}
            >
              {display}
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={(e) => {
                  e.stopPropagation();
                  removeTag(item);
                }}
                className="h-auto w-auto p-0.5 text-primary/60 hover:text-danger hover:bg-transparent"
                aria-label={`Remove ${display}`}
              >
                <X className="w-3 h-3" />
              </Button>
            </Badge>
          );
        })}
        <Input
          ref={inputRef}
          type="text"
          value={input}
          onChange={handleInputChange}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          placeholder={value.length === 0 ? placeholder : "Add more..."}
          className="flex-1 w-auto min-w-[120px] border-0 p-0 rounded-none bg-transparent placeholder:text-text-light/50 focus-visible:border-0"
        />
      </div>

      {/* Editable fields popover */}
      {editingTagIdx !== null && editableFields && (
        <div className="absolute z-30 w-full mt-1 border border-border bg-white rounded-lg shadow-lg p-4 space-y-3">
          {Object.entries(editableFields).map(([key, config]) => (
            <div key={key}>
              <Label className="block text-xs font-semibold text-text-light mb-1">
                {config.label}{" "}
                <span className="font-normal text-text-light/60">(optional)</span>
              </Label>
              <Input
                type="text"
                value={editingValues[key] ?? ""}
                onChange={(e) =>
                  setEditingValues((prev) => ({ ...prev, [key]: e.target.value }))
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter") { e.preventDefault(); savePopover(); }
                  if (e.key === "Escape") setEditingTagIdx(null);
                }}
                placeholder={config.placeholder}
                autoFocus
                className="border p-2"
              />
            </div>
          ))}
          <div className="flex justify-end gap-2 pt-1">
            <Button
              type="button"
              variant="outline"
              size="xs"
              onClick={() => setEditingTagIdx(null)}
              className="px-3"
            >
              Cancel
            </Button>
            <Button
              type="button"
              size="xs"
              onClick={savePopover}
              className="px-3"
            >
              Save
            </Button>
          </div>
        </div>
      )}

      {/* Suggestions dropdown */}
      <div
        className={`absolute z-20 w-full mt-1 border border-border bg-white rounded-lg shadow-lg overflow-hidden transition-all duration-200 origin-top ${
          showDropdown && editingTagIdx === null
            ? "opacity-100 scale-y-100 translate-y-0 pointer-events-auto"
            : "opacity-0 scale-y-95 -translate-y-1 pointer-events-none"
        }`}
      >
        {filtered.length > 0 ? (
          <ul className="max-h-48 overflow-y-auto divide-y divide-border/50">
            {filtered.map((s, idx) => (
              <li
                key={s}
                onClick={() => addTag(s)}
                className={`px-4 py-2 text-sm cursor-pointer flex items-center justify-between transition-colors ${
                  idx === activeIndex
                    ? "bg-primary/10 text-primary font-medium"
                    : "hover:bg-primary/5 hover:text-primary"
                }`}
                onMouseEnter={() => setActiveIndex(idx)}
                onMouseLeave={() => setActiveIndex(-1)}
              >
                <span>{highlightMatch(s, input)}</span>
                <Search className="w-3.5 h-3.5 text-text-light/40 ml-2 flex-shrink-0" />
              </li>
            ))}
          </ul>
        ) : (
          <div className="px-4 py-3 text-sm text-text-light text-center">
            {input.trim() ? (
              <>
                <span className="text-text/70">No matches. Press </span>
                <kbd className="inline-block px-1.5 py-0.5 text-xs bg-background border border-border rounded">
                  Enter
                </kbd>
                <span className="text-text/70"> to add "{input.trim()}"</span>
              </>
            ) : (
              <span className="text-text/50">
                Start typing to see suggestions
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
