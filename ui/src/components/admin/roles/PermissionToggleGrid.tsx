import { useState } from "react";
import { Trash2 } from "lucide-react";

import { Checkbox } from "@/components/ui/checkbox";
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
   *  source of which verbs a resource may be granted. */
  catalog: RbacCatalogResource[];
  /** Current grants. Its keys are the resources currently added to the role. */
  value: GrantMap;
  /** Always called with a brand-new GrantMap object (and a new array for the
   *  affected resource) — never mutates `value`. */
  onChange: (next: GrantMap) => void;
  /** Remove a resource and every one of its toggles in one action. */
  onRemoveResource: (resource: string) => void;
  /** Disables every checkbox and remove control (e.g. while a save is in flight). */
  disabled?: boolean;
}

const EMPTY_HINT =
  "No resources yet. Choose Add resources to pick what this role can act on.";

export function PermissionToggleGrid({
  catalog,
  value,
  onChange,
  onRemoveResource,
  disabled,
}: PermissionToggleGridProps) {
  const isMobile = useIsMobile();
  const [activeResource, setActiveResource] = useState<string | null>(null);

  const catalogByResource = new Map(
    catalog.map((entry) => [entry.resource, entry] as const)
  );

  // Editable resources = the keys of `value`, resolved against the catalog and
  // ordered by their human label. A key with no catalog entry is dropped — the
  // catalog is the allow-list, nothing else.
  const addedResources = Object.keys(value)
    .map((resource) => catalogByResource.get(resource))
    .filter((entry): entry is RbacCatalogResource => entry !== undefined)
    .sort((a, b) => a.label.localeCompare(b.label));

  const verbCount = (resource: string) => (value[resource] ?? []).length;

  const toggleVerb = (resource: string, verb: string) => {
    const current = value[resource] ?? [];
    const nextVerbs = current.includes(verb)
      ? current.filter((v) => v !== verb)
      : [...current, verb];
    onChange({ ...value, [resource]: nextVerbs });
  };

  if (addedResources.length === 0) {
    return (
      <div className="flex gap-8">
        <div className="hidden w-1/3 md:block">
          <p className="text-xs text-text-light">{EMPTY_HINT}</p>
        </div>
        <div className="flex flex-1 items-center justify-center py-12 text-center text-sm text-text-light">
          {EMPTY_HINT}
        </div>
      </div>
    );
  }

  const verbRows = (entry: RbacCatalogResource) => (
    <div className="flex flex-wrap gap-4">
      {entry.verbs.map((verb) => (
        <span key={verb.value} className="flex items-center gap-2 text-sm">
          <Checkbox
            checked={(value[entry.resource] ?? []).includes(verb.value)}
            disabled={disabled}
            aria-label={`${verb.label} on ${entry.label}`}
            onCheckedChange={() => toggleVerb(entry.resource, verb.value)}
          />
          <span>{verb.label}</span>
        </span>
      ))}
    </div>
  );

  const removeControl = (entry: RbacCatalogResource) => (
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
  );

  if (isMobile) {
    return (
      <Accordion type="multiple" className="md:hidden">
        {addedResources.map((entry) => (
          <AccordionItem key={entry.resource} value={entry.resource}>
            <div className="flex items-center justify-between gap-2">
              <AccordionTrigger className="flex-1">
                <span>
                  {entry.label}
                  <span className="ml-2 text-xs text-text-light">
                    {verbCount(entry.resource)}
                  </span>
                </span>
              </AccordionTrigger>
              {removeControl(entry)}
            </div>
            <AccordionContent>{verbRows(entry)}</AccordionContent>
          </AccordionItem>
        ))}
      </Accordion>
    );
  }

  return (
    <div className="flex gap-8">
      <ul className="w-1/3 space-y-1">
        {addedResources.map((entry) => (
          <li
            key={entry.resource}
            className={cn(
              "flex items-center justify-between rounded-md px-3 py-2 text-sm",
              activeResource === entry.resource && "bg-muted text-primary"
            )}
          >
            <button
              type="button"
              className="flex-1 text-left"
              onClick={() => setActiveResource(entry.resource)}
            >
              {entry.label}
              <span className="ml-2 text-xs text-text-light">
                {verbCount(entry.resource)}
              </span>
            </button>
            {removeControl(entry)}
          </li>
        ))}
      </ul>

      <div className="flex-1 space-y-6">
        {addedResources.map((entry) => (
          <section key={entry.resource}>
            <h3 className="mb-2 text-sm font-medium">{entry.label}</h3>
            {verbRows(entry)}
          </section>
        ))}
      </div>
    </div>
  );
}
