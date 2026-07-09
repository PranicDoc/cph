import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

interface StatusBadgeProps {
  active: boolean
  activeLabel?: string
  inactiveLabel?: string
  className?: string
}

/** Green/gray active-state pill used across admin lists. */
export function StatusBadge({
  active,
  activeLabel = "Active",
  inactiveLabel = "Inactive",
  className,
}: StatusBadgeProps) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500",
        className
      )}
    >
      {active ? activeLabel : inactiveLabel}
    </Badge>
  )
}
