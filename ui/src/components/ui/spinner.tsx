import { Loader2 } from "lucide-react"

import { cn } from "@/lib/utils"

const sizeClasses = {
  sm: "size-4",
  default: "size-5",
  lg: "size-8",
} as const

function Spinner({
  className,
  size = "default",
  ...props
}: React.ComponentProps<"svg"> & { size?: keyof typeof sizeClasses }) {
  return (
    <Loader2
      data-slot="spinner"
      role="status"
      aria-label="Loading"
      className={cn("animate-spin text-primary", sizeClasses[size], className)}
      {...props}
    />
  )
}

export { Spinner }
