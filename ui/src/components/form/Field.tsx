import type { ReactNode } from "react"

import { cn } from "@/lib/utils"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

/**
 * Bridge between @tanstack/react-form field APIs and the shadcn primitives.
 * Every form in the app renders its leaf controls through these helpers so
 * label, error, and control styling stay consistent.
 */

// TanStack form field api — kept loose to match the app's untyped `form.Field
// children={(field) => ...}` usage.
export type TanstackField = {
  state: {
    value: any
    meta: { errors: any[] }
  }
  handleChange: (value: any) => void
  handleBlur: () => void
}

export function FieldError({ errors }: { errors: any[] | undefined }) {
  if (!errors || errors.length === 0) return null
  return <p className="text-danger text-xs mt-1">{errors.join(", ")}</p>
}

export function FieldWrapper({
  label,
  required,
  error,
  className,
  labelClassName,
  action,
  children,
}: {
  label?: ReactNode
  required?: boolean
  error?: any[]
  className?: string
  labelClassName?: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <div className={className}>
      {(label || action) && (
        <div className="flex items-center justify-between mb-2">
          {label && (
            <Label
              className={cn(
                "block text-sm font-semibold text-text-light",
                labelClassName
              )}
            >
              {label} {required && "*"}
            </Label>
          )}
          {action}
        </div>
      )}
      {children}
      <FieldError errors={error} />
    </div>
  )
}

export function TextField({
  field,
  type = "text",
  placeholder,
  disabled,
  className,
  trimSpaces = false,
}: {
  field: TanstackField
  type?: string
  placeholder?: string
  disabled?: boolean
  className?: string
  trimSpaces?: boolean
}) {
  const value =
    trimSpaces && typeof field.state.value === "string"
      ? field.state.value.replace(/\s+/g, "")
      : field.state.value ?? ""
  return (
    <Input
      type={type}
      value={value}
      onBlur={field.handleBlur}
      onChange={(e) => field.handleChange(e.target.value)}
      disabled={disabled}
      aria-invalid={field.state.meta.errors.length > 0 || undefined}
      className={cn(disabled && "bg-gray-50", className)}
      placeholder={placeholder}
    />
  )
}

export function TextareaField({
  field,
  rows = 4,
  placeholder,
  disabled,
  className,
}: {
  field: TanstackField
  rows?: number
  placeholder?: string
  disabled?: boolean
  className?: string
}) {
  return (
    <Textarea
      rows={rows}
      value={field.state.value ?? ""}
      onBlur={field.handleBlur}
      onChange={(e) => field.handleChange(e.target.value)}
      disabled={disabled}
      aria-invalid={field.state.meta.errors.length > 0 || undefined}
      className={cn("p-3", disabled && "bg-gray-50", className)}
      placeholder={placeholder}
    />
  )
}

export function SelectField({
  field,
  options,
  placeholder,
  disabled,
  className,
}: {
  field: TanstackField
  options: { label: string; value: string }[]
  placeholder?: string
  disabled?: boolean
  className?: string
}) {
  return (
    <Select
      value={field.state.value || ""}
      onValueChange={(v) => field.handleChange(v)}
      disabled={disabled}
    >
      <SelectTrigger
        onBlur={field.handleBlur}
        aria-invalid={field.state.meta.errors.length > 0 || undefined}
        className={cn("w-full", disabled && "bg-gray-50", className)}
      >
        <SelectValue placeholder={placeholder || "Select..."} />
      </SelectTrigger>
      <SelectContent>
        {options.map((opt) => (
          <SelectItem key={opt.value} value={opt.value}>
            {opt.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export function CheckboxField({
  field,
  label,
  className,
  wrapperClassName,
  labelClassName,
}: {
  field: TanstackField
  label?: ReactNode
  className?: string
  wrapperClassName?: string
  labelClassName?: string
}) {
  return (
    <label
      className={cn("flex items-center gap-2 cursor-pointer", wrapperClassName)}
    >
      <Checkbox
        checked={Boolean(field.state.value)}
        onBlur={field.handleBlur}
        onCheckedChange={(checked) => field.handleChange(checked === true)}
        className={className}
      />
      {label && (
        <span className={labelClassName || "text-sm"}>{label}</span>
      )}
    </label>
  )
}
