import { useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ChevronLeft } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  PermissionToggleGrid,
  type GrantMap,
} from "@/components/admin/roles/PermissionToggleGrid";
import { ResourcePickerDialog } from "@/components/admin/roles/ResourcePickerDialog";
import { useRbacCatalog } from "@/hooks/admin/useRbacCatalog";
import { useRoleDetail } from "@/hooks/admin/useRbacRoles";

// A stable, order-independent string form of the editable state. Trims strings,
// treats a null/"" description alike, and sorts each resource's verb array so
// re-checking then un-checking the same box returns the form to its baseline.
function normalizeSnapshot(
  name: string,
  description: string,
  grants: GrantMap,
): string {
  const normalizedGrants: Record<string, string[]> = {};
  Object.keys(grants)
    .sort()
    .forEach((resource) => {
      normalizedGrants[resource] = [...(grants[resource] ?? [])].sort();
    });
  return JSON.stringify({
    name: name.trim(),
    description: description.trim(),
    grants: normalizedGrants,
  });
}

/**
 * UI-SPEC Screens B (`/admin/roles/new`) and C (`/admin/roles/:id/edit`).
 * One component, both modes — `isEdit` is derived from the `:id` route param.
 *
 * The form does not render until the permission catalog has loaded (and, in
 * edit mode, until the saved role has loaded too); a catalog failure replaces
 * the form with an explanatory retry block. D-10's "at least one granting
 * resource" gate is evaluated on submit as inline errors, never as a
 * permanently-disabled button.
 *
 * This plan (38-09) does NOT register the routes — plan 38-10 does.
 */
export default function RoleFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const catalogQuery = useRbacCatalog();
  const roleQuery = useRoleDetail(id);
  const catalog = catalogQuery.data;

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [grants, setGrants] = useState<GrantMap>({});
  // Create mode is "seeded" from the start; edit mode waits for the role fetch.
  const [seeded, setSeeded] = useState(!isEdit);
  const baselineRef = useRef<string>(normalizeSnapshot("", "", {}));

  const [nameError, setNameError] = useState<string | null>(null);
  const [resourceError, setResourceError] = useState<string | null>(null);
  const [droppedNote, setDroppedNote] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  // -- Edit prefill: seed name / description / grants once the role resolves --
  useEffect(() => {
    if (!isEdit || seeded || !roleQuery.data) return;
    const detail = roleQuery.data;
    const seededGrants: GrantMap = {};
    detail.grants.forEach((grant) => {
      seededGrants[grant.resource] = [...grant.operations];
    });
    setName(detail.name);
    setDescription(detail.description ?? "");
    setGrants(seededGrants);
    baselineRef.current = normalizeSnapshot(
      detail.name,
      detail.description ?? "",
      seededGrants,
    );
    setSeeded(true);
  }, [isEdit, seeded, roleQuery.data]);

  const labelForResource = (resource: string) =>
    catalog?.find((entry) => entry.resource === resource)?.label ?? resource;

  const applyGrants = (next: GrantMap) => {
    setGrants(next);
    setResourceError(null);
  };

  // Picker confirm: merge the chosen resources in, each starting with zero verbs.
  const handlePickerConfirm = (resources: string[]) => {
    setGrants((prev) => {
      const next = { ...prev };
      resources.forEach((resource) => {
        if (!(resource in next)) next[resource] = [];
      });
      return next;
    });
    setResourceError(null);
  };

  const handleRemoveResource = (resource: string) => {
    setGrants((prev) => {
      const next = { ...prev };
      delete next[resource];
      return next;
    });
    setResourceError(null);
  };

  // Validation gate (D-10 / ROLE-03), evaluated on submit. Returns the payload
  // when the form is valid, or null after rendering the specific inline errors.
  const buildPayloadOrErrors = () => {
    const trimmedName = name.trim();
    const grantingEntries = Object.entries(grants).filter(
      ([, verbs]) => verbs.length > 0,
    );
    const droppedResources = Object.keys(grants).filter(
      (resource) => (grants[resource] ?? []).length === 0,
    );

    setNameError(null);
    setResourceError(null);
    setDroppedNote(null);

    let valid = true;
    if (!trimmedName) {
      setNameError("Enter a role name.");
      valid = false;
    }
    if (grantingEntries.length < 1) {
      setResourceError("Add at least one resource before saving.");
      valid = false;
    } else if (droppedResources.length > 0) {
      const labels = droppedResources.map(labelForResource).join(", ");
      setDroppedNote(
        `No permissions were toggled for ${labels}, so ${
          droppedResources.length > 1 ? "they were" : "it was"
        } left out.`,
      );
    }

    if (!valid) return null;

    return {
      name: trimmedName,
      description: description.trim() ? description.trim() : null,
      grants: grantingEntries.map(([resource, operations]) => ({
        resource,
        operations,
      })),
    };
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    buildPayloadOrErrors();
    // The save flow (request, success toast, server-error branching) is wired
    // in Task 2.
  };

  const header = (
    <div className="mb-6 flex items-center gap-2">
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Back to roles"
        onClick={() => navigate("/admin/roles")}
      >
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <h1 className="text-xl font-bold text-text md:text-2xl">
        {isEdit ? "Edit role" : "Create role"}
      </h1>
      {isEdit && id && (
        <Button
          type="button"
          variant="link"
          className="ml-auto"
          onClick={() => navigate(`/admin/roles/${id}/users`)}
        >
          Assigned users
        </Button>
      )}
    </div>
  );

  if (catalogQuery.isError) {
    return (
      <div>
        {header}
        <div className="flex max-w-3xl flex-col items-start gap-3 rounded-xl border border-destructive/20 bg-destructive/10 px-4 py-6">
          <p className="text-sm text-destructive">
            Couldn't load the permission catalog. The role form needs it to know
            which permissions each resource allows.
          </p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => catalogQuery.refetch()}
          >
            Retry
          </Button>
        </div>
      </div>
    );
  }

  if (!catalog || !seeded) {
    return (
      <div>
        {header}
        <div
          className="max-w-3xl space-y-4"
          data-testid="role-form-skeleton"
          aria-busy="true"
        >
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      </div>
    );
  }

  return (
    <div>
      {header}

      <form onSubmit={handleSubmit} className="max-w-3xl space-y-6 pb-24">
        <div>
          <div className="flex items-center gap-1">
            <Label htmlFor="role-name">Name</Label>
            <span aria-hidden="true" className="text-destructive">
              *
            </span>
          </div>
          <Input
            id="role-name"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setNameError(null);
            }}
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? "role-name-error" : undefined}
          />
          {nameError && (
            <p id="role-name-error" className="mt-1 text-xs text-destructive">
              {nameError}
            </p>
          )}
        </div>

        <div>
          <Label htmlFor="role-description">Description</Label>
          <Textarea
            id="role-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
          <p className="mt-1 text-xs text-text-light">Optional.</p>
        </div>

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-medium text-text">Permissions</h2>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setPickerOpen(true)}
            >
              Add resources
            </Button>
          </div>
          <PermissionToggleGrid
            catalog={catalog}
            value={grants}
            onChange={applyGrants}
            onRemoveResource={handleRemoveResource}
          />
        </div>

        <div className="sticky bottom-0 -mx-4 flex items-center gap-3 border-t border-border bg-card px-4 py-3">
          <div className="flex-1 space-y-1 text-left">
            {resourceError && (
              <p className="text-xs text-destructive">{resourceError}</p>
            )}
            {droppedNote && (
              <p className="text-xs text-text-light">{droppedNote}</p>
            )}
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate("/admin/roles")}
          >
            Discard and go back
          </Button>
          <Button type="submit">
            {isEdit ? "Save changes" : "Create role"}
          </Button>
        </div>
      </form>

      <ResourcePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        catalog={catalog}
        alreadyAdded={Object.keys(grants)}
        onConfirm={handlePickerConfirm}
      />
    </div>
  );
}
