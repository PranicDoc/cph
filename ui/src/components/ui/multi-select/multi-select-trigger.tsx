import { ChevronDown, Loader2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import { cn } from "@/lib/utils";

import { MultiSelectTriggerProps } from "./types";

export function MultiSelectTrigger<T>({
  value,
  getOptionKey,
  getOptionLabel,
  renderBadge,
  placeholder = "Select...",
  maxBadges = 2,
  className,
  disabled,
  loading
}: MultiSelectTriggerProps<T>) {
  const visible = value.slice(0, maxBadges);
  const remaining = value.length - visible.length;

  return (
    <Button
      type="button"
      variant="outline"
      role="combobox"
      disabled={disabled}
      className={cn(
        "w-full h-10 justify-between overflow-hidden px-3",
        className
      )}
    >
      <div className="flex min-w-0 flex-1 items-center gap-1 overflow-hidden">
        {value.length === 0 ? (
          <span className="truncate text-muted-foreground">{placeholder}</span>
        ) : (
          <>
            {visible.map((item) => (
              <Badge
                key={getOptionKey(item)}
                variant="secondary"
                className="shrink-0"
              >
                {renderBadge ? renderBadge(item) : getOptionLabel(item)}
              </Badge>
            ))}

            {remaining > 0 && (
              <Badge variant="outline" className="shrink-0">
                +{remaining}
              </Badge>
            )}
          </>
        )}
      </div>
      {loading ? (
        <Loader2 className="ml-2 h-4 w-4 shrink-0 animate-spin" />
      ) : (
        <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-60" />
      )}
    </Button>
  );
}
