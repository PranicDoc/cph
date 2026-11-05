import { cn } from "@/lib/utils";

/**
 * The live "120/400" under a typed field. It turns red over the limit, which only happens for text
 * saved before the limit existed: typing stops at the limit, but such text must be shortened to save.
 */
export function CharacterCount({ value, max }: { value: string | null | undefined; max: number }) {
  const length = value?.length ?? 0;
  return (
    <p className={cn("text-xs text-right text-text-light", length > max && "text-danger font-medium")}>
      {length}/{max}
    </p>
  );
}
