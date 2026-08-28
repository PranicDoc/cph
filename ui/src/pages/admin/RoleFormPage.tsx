import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { AlertCircle } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  PermissionToggleGrid,
  type GrantMap,
} from "@/components/admin/roles/PermissionToggleGrid";
import { ResourcePickerDialog } from "@/components/admin/roles/ResourcePickerDialog";
import { RoleUsersTab } from "@/components/admin/roles/RoleUsersTab";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useUnsavedChangesPrompt } from "@/hooks/useUnsavedChangesPrompt";
import { useRbacCatalog } from "@/hooks/admin/useRbacCatalog";
import {
  useRoleDetail,
  useCreateRole,
  useUpdateRole,
} from "@/hooks/admin/useRbacRoles";
import { extractDetail } from "@/lib/utils";
import { cn } from "@/lib/utils";

function isSelfLockout(error: unknown): boolean {
  const data = (
    error as { response?: { data?: { type?: string; detail?: string } } }
  )?.response?.data;
  const type = data?.type ?? "";
  const detail = data?.detail ?? "";
  return (
    type.includes("self-lockout") || /nobody able to manage roles/i.test(detail)
  );
}

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
 * Role Management mockup Screens 4–9. One component covers create
 * (`/admin/roles/new`) and edit (`/admin/roles/:id/edit`); edit mode adds a
 * Permissions / Users-assigned tab strip, a change-diff banner, and pre-fills
 * from the saved role. Submit opens a review modal (Screen 7) before the
 * mutation actually fires. D-10's "name + at least one resource" gate renders
 * as a top alert banner plus inline field errors, never a disabled button.
 */
export default function RoleFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const [sp, setSp] = useSearchParams();

  const tab = isEdit && sp.get("tab") === "users" ? "users" : "permissions";
  const setTab = (next: "permissions" | "users") => {
    setSp(
      (prev) => {
        const p = new URLSearchParams(prev);
        if (next === "users") p.set("tab", "users");
        else p.delete("tab");
        return p;
      },
      { replace: true },
    );
  };

  const catalogQuery = useRbacCatalog();
  const roleQuery = useRoleDetail(id);
  const catalog = catalogQuery.data;

  const createRole = useCreateRole();
  const updateRole = useUpdateRole();
  const activeMutation = isEdit ? updateRole : createRole;

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [grants, setGrants] = useState<GrantMap>({});
  const [seeded, setSeeded] = useState(!isEdit);
  const baselineRef = useRef<string>(normalizeSnapshot("", "", {}));
  const [baselineGrants, setBaselineGrants] = useState<GrantMap>({});

  const [nameError, setNameError] = useState<string | null>(null);
  const [resourceError, setResourceError] = useState<string | null>(null);
  const [lockoutError, setLockoutError] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [saved, setSaved] = useState(false);

  const isDirty =
    !saved &&
    seeded &&
    normalizeSnapshot(name, description, grants) !== baselineRef.current;
  const blocker = useUnsavedChangesPrompt(isDirty);

  useEffect(() => {
    if (saved) navigate("/admin/roles");
  }, [saved, navigate]);

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
    setBaselineGrants(seededGrants);
    baselineRef.current = normalizeSnapshot(
      detail.name,
      detail.description ?? "",
      seededGrants,
    );
    setSeeded(true);
  }, [isEdit, seeded, roleQuery.data]);

  const labelForResource = (resource: string) =>
    catalog?.find((entry) => entry.resource === resource)?.label ?? resource;
  const labelForVerb = (resource: string, verb: string) =>
    catalog
      ?.find((entry) => entry.resource === resource)
      ?.verbs.find((v) => v.value === verb)?.label ?? verb;

  const applyGrants = (next: GrantMap) => {
    setGrants(next);
    setResourceError(null);
  };

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

  // -- Change diff (edit mode) --------------------------------------------
  const diff = useMemo(() => {
    if (!isEdit || !seeded) return { added: [], removed: [] } as const;
    const added: string[] = [];
    const removed: string[] = [];
    const resources = new Set([
      ...Object.keys(baselineGrants),
      ...Object.keys(grants),
    ]);
    resources.forEach((resource) => {
      const before = new Set(baselineGrants[resource] ?? []);
      const after = new Set(grants[resource] ?? []);
      after.forEach((v) => {
        if (!before.has(v))
          added.push(`${labelForResource(resource)}: ${labelForVerb(resource, v)}`);
      });
      before.forEach((v) => {
        if (!after.has(v))
          removed.push(
            `${labelForResource(resource)}: ${labelForVerb(resource, v)}`,
          );
      });
    });
    return { added, removed };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEdit, seeded, grants, baselineGrants, catalog]);

  const changeCount = diff.added.length + diff.removed.length;
  const detailsEdited =
    isEdit &&
    seeded &&
    (name.trim() !== (roleQuery.data?.name ?? "").trim() ||
      description.trim() !== (roleQuery.data?.description ?? "").trim());

  // -- Validation ------------------------------------------------------------
  const validationErrors = () => {
    const errs: string[] = [];
    if (!name.trim()) errs.push("enter a role name");
    const grantingCount = Object.values(grants).filter(
      (verbs) => verbs.length > 0,
    ).length;
    if (grantingCount < 1) errs.push("add at least one resource before saving");
    return errs;
  };
  const [bannerErrors, setBannerErrors] = useState<string[]>([]);

  const buildPayload = () => ({
    name: name.trim(),
    description: description.trim() ? description.trim() : null,
    grants: Object.entries(grants)
      .filter(([, verbs]) => verbs.length > 0)
      .map(([resource, operations]) => ({ resource, operations })),
  });

  const handleReviewClick = (event: FormEvent) => {
    event.preventDefault();
    setLockoutError(null);
    const errs = validationErrors();
    setBannerErrors(errs);
    setNameError(!name.trim() ? "Role name is required" : null);
    setResourceError(
      Object.values(grants).filter((v) => v.length > 0).length < 1
        ? "A role with no resources can't grant any access."
        : null,
    );
    if (errs.length > 0) return;
    setReviewOpen(true);
  };

  const handleSuccess = (savedName: string) => {
    toast.success(
      isEdit
        ? `Role "${savedName}" saved.`
        : `"${savedName}" is ready to assign to users.`,
    );
    setReviewOpen(false);
    setSaved(true);
  };

  const handleError = (error: unknown) => {
    setReviewOpen(false);
    const status = (error as { response?: { status?: number } })?.response
      ?.status;
    if (status === 400) {
      setBannerErrors(["fix the role name"]);
      setNameError(
        extractDetail(error) ?? "A role with that name already exists.",
      );
      return;
    }
    if (status === 409) {
      if (isEdit && isSelfLockout(error)) {
        setLockoutError(
          extractDetail(error) ??
            "This change would leave nobody able to manage roles, so it was blocked.",
        );
        return;
      }
      setBannerErrors(["fix the role name"]);
      setNameError(
        extractDetail(error) ?? "A role with that name already exists.",
      );
      return;
    }
    toast.error(extractDetail(error) || "Failed to save role");
  };

  const doSubmit = () => {
    const payload = buildPayload();
    if (isEdit && id) {
      updateRole.mutate(
        { id, body: payload },
        { onSuccess: () => handleSuccess(payload.name), onError: handleError },
      );
    } else {
      createRole.mutate(payload, {
        onSuccess: () => handleSuccess(payload.name),
        onError: handleError,
      });
    }
  };

  const breadcrumb = isEdit
    ? `Admin / Roles & Permissions / ${roleQuery.data?.name ?? "…"}`
    : "Admin / Roles & Permissions";

  const heading = (
    <div className="mb-4">
      <div className="mb-1.5 text-xs text-text-light">{breadcrumb}</div>
      <h1 className="text-xl font-bold text-text md:text-2xl">
        {isEdit ? "Edit role" : "Create role"}
      </h1>
    </div>
  );

  if (catalogQuery.isError) {
    return (
      <div>
        {heading}
        <div className="flex max-w-3xl flex-col items-start gap-3 rounded-xl border border-destructive/20 bg-destructive/10 px-4 py-6">
          <p className="text-sm text-destructive">
            Couldn't load the permission catalog. The role form needs it to know
            which permissions each resource allows.
          </p>
          <Button variant="outline" size="sm" onClick={() => catalogQuery.refetch()}>
            Retry
          </Button>
        </div>
      </div>
    );
  }

  if (!catalog || !seeded) {
    return (
      <div>
        {heading}
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

  const tabs = isEdit && (
    <div className="mb-5 flex gap-6 border-b border-border">
      {(["permissions", "users"] as const).map((t) => (
        <button
          key={t}
          type="button"
          onClick={() => setTab(t)}
          className={cn(
            "-mb-px border-b-2 border-transparent py-2.5 text-sm font-semibold text-text-light",
            tab === t && "border-primary text-primary",
          )}
        >
          {t === "permissions" ? "Permissions" : "Users assigned"}
        </button>
      ))}
    </div>
  );

  const addedCount = Object.keys(grants).length;

  return (
    <div>
      {heading}
      {tabs}

      {tab === "users" && id ? (
        <RoleUsersTab roleId={id} roleName={roleQuery.data?.name ?? "Role"} />
      ) : (
        <form onSubmit={handleReviewClick} className="max-w-3xl space-y-4">
          {bannerErrors.length > 0 && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-destructive" />
              <p className="text-[12.5px] leading-relaxed text-destructive">
                <strong>
                  {bannerErrors.length}{" "}
                  {bannerErrors.length === 1 ? "thing needs" : "things need"}{" "}
                  fixing
                </strong>{" "}
                — {bannerErrors.join(", and ")}.
              </p>
            </div>
          )}

          {/* Name + description card */}
          <div className="flex flex-col gap-5 rounded-xl border border-border bg-card p-6 md:flex-row">
            <div className="md:flex-1">
              <div className="mb-1.5 flex items-center gap-1">
                <Label htmlFor="role-name">Role name</Label>
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
            <div className="md:flex-[2]">
              <Label htmlFor="role-description" className="mb-1.5 block">
                Description
              </Label>
              <Textarea
                id="role-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
              <p className="mt-1 text-xs text-text-light">Optional.</p>
            </div>
          </div>

          {/* Change-diff banner (edit mode) */}
          {isEdit && (changeCount > 0 || detailsEdited) && (
            <div className="rounded-lg border border-primary/25 bg-primary/[0.06] px-4 py-3">
              <div className="mb-1.5 text-[12.5px] font-semibold text-primary-dark">
                {changeCount > 0
                  ? `${changeCount} unsaved change${
                      changeCount === 1 ? "" : "s"
                    } — ${diff.added.length} added, ${diff.removed.length} removed`
                  : "Role details edited"}
              </div>
              {diff.added.map((line) => (
                <div key={`+${line}`} className="text-xs text-green-700">
                  + {line} added
                </div>
              ))}
              {diff.removed.map((line) => (
                <div key={`-${line}`} className="text-xs text-destructive">
                  − {line} removed
                </div>
              ))}
            </div>
          )}

          {/* Resource permissions card */}
          <div className="flex flex-col overflow-hidden rounded-xl border border-border bg-card">
            <div className="flex items-center justify-between border-b border-border px-5 py-4">
              <div className="text-[13px] font-semibold text-text">
                Resource permissions
              </div>
              <div className="text-xs text-text-light">
                {addedCount} of {catalog.length} resources added
              </div>
            </div>
            <PermissionToggleGrid
              catalog={catalog}
              value={grants}
              onChange={applyGrants}
              onRemoveResource={handleRemoveResource}
              onAddResource={() => setPickerOpen(true)}
              baseline={isEdit ? baselineGrants : undefined}
              disabled={activeMutation.isPending}
            />
          </div>

          {resourceError && (
            <p className="text-xs text-destructive">{resourceError}</p>
          )}
          {lockoutError && (
            <p
              role="alert"
              className="rounded-lg border border-destructive/20 bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {lockoutError}
            </p>
          )}

          {/* Footer actions */}
          <div className="flex justify-end gap-2 pt-1">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate("/admin/roles")}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={activeMutation.isPending}>
              {activeMutation.isPending
                ? isEdit
                  ? "Saving…"
                  : "Creating…"
                : isEdit
                  ? "Review & save changes"
                  : "Review & save"}
            </Button>
          </div>
        </form>
      )}

      <ResourcePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        catalog={catalog}
        alreadyAdded={Object.keys(grants)}
        onConfirm={handlePickerConfirm}
      />

      {/* Review-before-save modal (Screen 7) */}
      <Dialog open={reviewOpen} onOpenChange={setReviewOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>
              Review "{name.trim() || "this role"}"
            </DialogTitle>
            <DialogDescription>
              Confirm the full permission mapping before saving. You can still go
              back and change anything.
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[420px] space-y-3 overflow-auto py-1">
            {Object.keys(grants)
              .sort((a, b) =>
                labelForResource(a).localeCompare(labelForResource(b)),
              )
              .map((resource) => {
                const verbs = grants[resource] ?? [];
                const readOnly = verbs.length > 0 && verbs.every((v) => v === "READ");
                return (
                  <div
                    key={resource}
                    className="rounded-lg border border-border px-3.5 py-3"
                  >
                    <div className="mb-1 flex items-center justify-between">
                      <div className="text-[13px] font-semibold text-text">
                        {labelForResource(resource)}
                      </div>
                      {readOnly && (
                        <span className="text-[10.5px] font-semibold text-amber-500">
                          READ ONLY
                        </span>
                      )}
                    </div>
                    <div
                      className={cn(
                        "text-[12.5px]",
                        verbs.length ? "text-text" : "text-text-light",
                      )}
                    >
                      {verbs.length
                        ? verbs
                            .map((v) => labelForVerb(resource, v))
                            .join(", ")
                        : "No permissions granted"}
                    </div>
                  </div>
                );
              })}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReviewOpen(false)}>
              Back to edit
            </Button>
            <Button onClick={doSubmit} disabled={activeMutation.isPending}>
              {isEdit ? "Confirm & save changes" : "Confirm & save role"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {blocker.state === "blocked" && (
        <ConfirmDialog
          open
          onOpenChange={(next) => {
            if (!next) blocker.reset?.();
          }}
          title="Discard unsaved changes?"
          description={
            addedCount > 0
              ? `You've configured permissions for ${addedCount} resource${
                  addedCount === 1 ? "" : "s"
                }. Leaving now will discard everything on this page.`
              : "You've changed this role but haven't saved. Leaving now loses those changes."
          }
          confirmLabel="Discard changes"
          cancelLabel="Keep editing"
          destructive
          onConfirm={() => blocker.proceed?.()}
        />
      )}
    </div>
  );
}
