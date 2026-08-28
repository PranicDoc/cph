import { useEffect, useState } from "react";
import { ChevronRight, Lock, Plus, Trash2 } from "lucide-react";

import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { cn } from "@/lib/utils";
import { useIsMobile } from "@/hooks/use-mobile";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

/** resource enum value -> selected verb enum values. */
export type GrantMap = Record<string, string[]>;

export interface PermissionToggleGridProps {
  /** The full permission catalog (GET /api/admin/roles/catalog) — the sole
   *  source of which verbs a resource may be granted, plus each verb's
   *  description and `locked` flag. */
  catalog: RbacCatalogResource[];
  /** Current grants. Its keys are the resources currently added to the role. */
  value: GrantMap;
  /** Always called with a brand-new GrantMap object (and a new array for the
   *  affected resource) — never mutates `value`. */
  onChange: (next: GrantMap) => void;
  /** Remove a resource and every one of its toggles in one action. */
  onRemoveResource: (resource: string) => void;
  /** Opens the resource-picker dialog (the "+ Add resource" control lives
   *  inside the left panel per the mockup). */
  onAddResource: () => void;
  /** The last-saved grant map, in edit mode. When present, a verb whose
   *  on/off state differs from its baseline shows a "CHANGED" badge. */
  baseline?: GrantMap;
  /** Disables every switch and remove control (e.g. while a save is in flight). */
  disabled?: boolean;
}

const EMPTY_TITLE = "No resources added yet";
const EMPTY_BODY =
  "Add the resources this role should access, then configure the permissions for each one.";

export function PermissionToggleGrid({
  catalog,
  value,
  onChange,
  onRemoveResource,
  onAddResource,
  baseline,
  disabled,
}: PermissionToggleGridProps) {
  const isMobile = useIsMobile();
  const [activeResource, setActiveResource] = useState<string | null>(null);

  const catalogByResource = new Map(
    catalog.map((entry) => [entry.resource, entry] as const),
  );

  // Added resources = keys of `value`, resolved against the catalog and ordered
  // by human label. A key with no catalog entry is dropped — the catalog is the
  // allow-list, nothing else.
  const addedResources = Object.keys(value)
    .map((resource) => catalogByResource.get(resource))
    .filter((entry): entry is RbacCatalogResource => entry !== undefined)
    .sort((a, b) => a.label.localeCompare(b.label));

  // Keep a valid selection: default to the first added resource, and recover if
  // the active one was removed.
  useEffect(() => {
    if (addedResources.length === 0) {
      if (activeResource !== null) setActiveResource(null);
      return;
    }
    if (
      activeResource === null ||
      !addedResources.some((e) => e.resource === activeResource)
    ) {
      setActiveResource(addedResources[0].resource);
    }
  }, [addedResources, activeResource]);

  const isOn = (resource: string, verb: string) =>
    (value[resource] ?? []).includes(verb);

  const grantedCount = (entry: RbacCatalogResource) =>
    entry.verbs.filter((v) => v.locked || isOn(entry.resource, v.value)).length;

  const changed = (resource: string, verb: string) => {
    if (!baseline) return false;
    const before = (baseline[resource] ?? []).includes(verb);
    return before !== isOn(resource, verb);
  };

  const toggleVerb = (resource: string, verb: string, locked: boolean) => {
    if (locked || disabled) return;
    const current = value[resource] ?? [];
    const nextVerbs = current.includes(verb)
      ? current.filter((v) => v !== verb)
      : [...current, verb];
    onChange({ ...value, [resource]: nextVerbs });
  };

  const addButton = (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      disabled={disabled}
      onClick={onAddResource}
    >
      <Plus className="h-4 w-4" />
      Add resource
    </Button>
  );

  if (addedResources.length === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 px-10 py-16 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-primary/10">
          <svg
            width="26"
            height="26"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            className="text-primary"
          >
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="7" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
            <rect x="14" y="14" width="7" height="7" rx="1" />
          </svg>
        </div>
        <div className="text-sm font-semibold text-text">{EMPTY_TITLE}</div>
        <p className="max-w-sm text-xs text-text-light">{EMPTY_BODY}</p>
        {addButton}
      </div>
    );
  }

  const verbRow = (entry: RbacCatalogResource, verb: RbacCatalogResource["verbs"][number]) => {
    const on = verb.locked || isOn(entry.resource, verb.value);
    return (
      <div
        key={verb.value}
        className="flex items-center justify-between gap-3 border-t border-muted py-2.5 first:border-t-0"
      >
        <div className="flex items-center gap-2">
          <div>
            <div className="text-[12.5px] font-semibold text-text">
              {verb.label}
            </div>
            <div className="text-[11.5px] text-text-light">
              {verb.locked
                ? "Required for this role — can't be removed"
                : verb.description}
            </div>
          </div>
          {changed(entry.resource, verb.value) && (
            <span className="rounded bg-primary/12 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-primary">
              Changed
            </span>
          )}
        </div>
        <div className="flex items-center gap-1.5">
          {verb.locked && <Lock className="h-3 w-3 text-primary" />}
          <Switch
            checked={on}
            disabled={disabled || verb.locked}
            aria-label={`${verb.label} on ${entry.label}`}
            onCheckedChange={() =>
              toggleVerb(entry.resource, verb.value, verb.locked)
            }
          />
        </div>
      </div>
    );
  };

  if (isMobile) {
    return (
      <div>
        <Accordion type="multiple">
          {addedResources.map((entry) => (
            <AccordionItem key={entry.resource} value={entry.resource}>
              <div className="flex items-center justify-between gap-2">
                <AccordionTrigger className="flex-1">
                  <span className="text-[12.5px] font-semibold text-text">
                    {entry.label}
                    <span className="ml-2 text-[11px] font-normal text-text-light">
                      {grantedCount(entry)}/{entry.verbs.length} granted
                    </span>
                  </span>
                </AccordionTrigger>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  disabled={disabled}
                  aria-label={`Remove ${entry.label}`}
                  onClick={() => onRemoveResource(entry.resource)}
                >
                  <Trash2 />
                </Button>
              </div>
              <AccordionContent>
                {entry.verbs.map((verb) => verbRow(entry, verb))}
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
        <div className="border-t border-border px-3 py-2.5">{addButton}</div>
      </div>
    );
  }

  const selected =
    addedResources.find((e) => e.resource === activeResource) ??
    addedResources[0];

  return (
    <div className="flex min-h-[280px] flex-1">
      {/* Left panel — added-resource list */}
      <div className="flex w-[250px] flex-none flex-col border-r border-border">
        <ul className="flex-1 overflow-auto">
          {addedResources.map((entry) => {
            const active = entry.resource === selected.resource;
            return (
              <li key={entry.resource}>
                <div
                  className={cn(
                    "flex cursor-pointer items-center justify-between gap-1.5 border-l-[3px] border-l-transparent px-3.5 py-2.5",
                    active && "border-l-primary bg-primary/10",
                  )}
                  onClick={() => setActiveResource(entry.resource)}
                >
                  <div className="min-w-0 overflow-hidden">
                    <div className="truncate text-[12.5px] font-semibold text-text">
                      {entry.label}
                    </div>
                    <div className="truncate text-[11px] text-text-light">
                      {grantedCount(entry)}/{entry.verbs.length} granted
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-xs"
                      disabled={disabled}
                      aria-label={`Remove ${entry.label}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        onRemoveResource(entry.resource);
                      }}
                    >
                      <Trash2 />
                    </Button>
                    <ChevronRight className="h-3.5 w-3.5 text-text-light" />
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
        <div className="border-t border-border px-3 py-2.5">{addButton}</div>
      </div>

      {/* Right panel — permissions for the selected resource */}
      <div className="flex-1 overflow-auto px-5 py-4">
        <div className="text-sm font-semibold text-text">{selected.label}</div>
        <div className="mb-2.5 text-xs text-text-light">
          Configure what this role can do with {selected.label.toLowerCase()}.
        </div>
        {selected.verbs.map((verb) => verbRow(selected, verb))}
      </div>
    </div>
  );
}
