import { useEffect, useState } from "react";

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { MultiSelectList } from "@/components/ui/multi-select/multi-select-list";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

export interface ResourcePickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The full permission catalog. */
  catalog: RbacCatalogResource[];
  /** Resource enum values already on the role — never re-offered. */
  alreadyAdded: string[];
  /** Fires with the newly selected resource enum values only. */
  onConfirm: (resources: string[]) => void;
}

/**
 * Searchable resource picker. Offers only catalog resources that (a) have at
 * least one grantable verb and (b) are not already on the role (D-03). Search
 * matches the human label — the raw enum token never drives the search.
 * Composes Dialog + DialogContent + DialogTitle inline (the ModalShell
 * pattern — there is nothing to import) around MultiSelectList.
 */
export function ResourcePickerDialog({
  open,
  onOpenChange,
  catalog,
  alreadyAdded,
  onConfirm,
}: ResourcePickerDialogProps) {
  const [selected, setSelected] = useState<RbacCatalogResource[]>([]);

  // Grantable resources minus the ones already on the role. Recomputed every
  // render (and specifically re-evaluated whenever `open` flips) so a resource
  // added in a previous round is not offered again.
  const options = catalog.filter(
    (entry) => entry.verbs.length > 0 && !alreadyAdded.includes(entry.resource)
  );

  // Local selection resets to empty every time the dialog opens.
  useEffect(() => {
    if (open) setSelected([]);
  }, [open]);

  const handleConfirm = () => {
    onConfirm(selected.map((entry) => entry.resource));
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add resources</DialogTitle>
        </DialogHeader>

        <MultiSelectList<RbacCatalogResource>
          options={options}
          value={selected}
          onChange={(next) => setSelected(next)}
          getOptionKey={(entry) => entry.resource}
          getOptionLabel={(entry) => entry.label}
          searchPlaceholder="Search resources"
          emptyText="No resources left to add."
        />

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Keep current resources
          </Button>
          <Button onClick={handleConfirm} disabled={selected.length === 0}>
            Add selected
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
