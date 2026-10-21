import { useMemo, useRef, useState } from "react";
import { useUiVersion } from "../../ui-version";
import { useForm } from "@tanstack/react-form";
import type { ColumnDef } from "@tanstack/react-table";
import { Plus, Pencil, Trash2, Lock, Upload } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import {
  usePranaColors,
  useChakras,
  useProtocolMappings,
  useProtocols,
  useCreateColor,
  useUpdateColor,
  useDeleteColor,
  useCreateChakra,
  useUpdateChakra,
  useDeleteChakra,
  useCreateMapping,
  useDeleteMapping,
  useCreateProtocol,
  useUpdateProtocol,
  useDeleteProtocol,
  useSubmitProtocolImport,
  useProtocolImportJobs,
  useChakraColorGroupsForProtocol,
  useAddProtocolChakraColors,
  useUpdateProtocolChakraColor,
  useDeleteProtocolChakraColor,
  useWipeMasterData,
  type PranaColor,
  type Chakra,
  type Protocol,
  type PranaColorPayload,
  type ChakraPayload,
  type ProtocolPayload,
  type ProtocolImportJobView,
  type ProtocolChakraColorGroup,
  type MasterDataType,
} from "../../hooks/admin/useMasterData";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { DataTable } from "@/components/ui/data-table";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { SelectField, TextField, TextareaField } from "@/components/form/Field";
import { MultiSelect } from "@/components/ui/multi-select/multi-select";
import { cn } from "@/lib/utils";

// ── Shared helpers ────────────────────────────────────────────────────────────

function SourceBadge({ healerId }: { healerId?: string | null }) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        healerId ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
      )}
    >
      {healerId ? "Custom" : "System"}
    </Badge>
  );
}

function ColorSwatch({ hex }: { hex?: string }) {
  return (
    <span
      className="inline-block w-5 h-5 rounded-full border border-border flex-shrink-0"
      style={{ backgroundColor: hex ?? "#ccc" }}
    />
  );
}

function LockIcon() {
  return (
    <div className="flex justify-end">
      <Lock className="w-4 h-4 text-text-light/40" />
    </div>
  );
}

// ── Shared modal shell ────────────────────────────────────────────────────────

function ModalShell({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 gap-0 max-h-[90vh] overflow-y-auto max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        <DialogTitle className="text-lg font-bold text-text mb-4">{title}</DialogTitle>
        {children}
      </DialogContent>
    </Dialog>
  );
}

const fieldCls = "border px-3 py-2.5";

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <Label className="block text-xs text-text-light mb-1">{label}</Label>
      {children}
    </div>
  );
}

function ModalFooter({
  isPending,
  submitLabel,
  onClose,
}: {
  isPending: boolean;
  submitLabel: string;
  onClose: () => void;
}) {
  return (
    <div className="flex gap-2 pt-1">
      <Button type="submit" disabled={isPending} className="flex-1 py-2.5 h-auto">
        {isPending ? "Saving…" : submitLabel}
      </Button>
      <Button
        type="button"
        variant="outline"
        onClick={onClose}
        className="px-4 py-2.5 h-auto font-normal text-text"
      >
        Cancel
      </Button>
    </div>
  );
}

// ── Row action buttons (edit/delete or lock) ─────────────────────────────────

function RowActions({
  onEdit,
  onDelete,
  deleting,
}: {
  onEdit?: () => void;
  onDelete: () => void;
  deleting: boolean;
}) {
  return (
    <div className="flex gap-1 justify-end">
      {onEdit && (
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onEdit}
          className="h-auto w-auto p-1.5 text-text-light"
        >
          <Pencil className="w-4 h-4" />
        </Button>
      )}
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={onDelete}
        disabled={deleting}
        className="h-auto w-auto p-1.5 hover:bg-red-50 text-red-400 hover:text-red-400"
      >
        <Trash2 className="w-4 h-4" />
      </Button>
    </div>
  );
}

// ── Prana Colors Tab ──────────────────────────────────────────────────────────

function ColorModal({
  initial,
  onClose,
}: {
  initial?: PranaColor;
  onClose: () => void;
}) {
  const [error, setError] = useState("");
  const create = useCreateColor();
  const update = useUpdateColor();

  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      code: initial?.code ?? "",
      colorHex: initial?.colorHex ?? "#ffffff",
      properties: initial?.properties?.join("\n") ?? "",
    },
    onSubmit: async ({ value }) => {
      setError("");
      const payload: PranaColorPayload = {
        name: value.name,
        code: value.code.toUpperCase(),
        colorHex: value.colorHex,
        properties: value.properties
          .split("\n")
          .map((p) => p.trim())
          .filter(Boolean),
        healerId: initial ? initial.healerId : null,
      };
      try {
        if (initial) {
          await update.mutateAsync({ id: initial.id, payload });
        } else {
          await create.mutateAsync(payload);
        }
        onClose();
      } catch {
        setError("Failed to save. Please try again.");
      }
    },
  });

  const isPending = create.isPending || update.isPending;

  return (
    <ModalShell title={initial ? "Edit Prana Color" : "Add Prana Color"} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          form.handleSubmit();
        }}
        className="space-y-3"
      >
        <Field label="Name">
          <form.Field
            name="name"
            children={(field) => <TextField field={field} className={fieldCls} />}
          />
        </Field>
        <Field label="Code (e.g. VIOLET)">
          <form.Field
            name="code"
            children={(field) => (
              <TextField field={field} disabled={!!initial} className={fieldCls} />
            )}
          />
        </Field>
        <Field label="Color">
          <form.Field
            name="colorHex"
            children={(field) => (
              <div className="flex items-center gap-2">
                <Input
                  type="color"
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  className="w-10 h-10 rounded cursor-pointer border p-0.5"
                />
                <TextField field={field} placeholder="#ffffff" className={fieldCls} />
              </div>
            )}
          />
        </Field>
        <Field label="Healing Properties">
          <form.Field
            name="properties"
            children={(field) => (
              <TextareaField
                field={field}
                className={cn(fieldCls, "p-2.5")}
                placeholder="One property per line, e.g.&#10;Strengthening&#10;Warming&#10;Energizing"
              />
            )}
          />
        </Field>
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <ModalFooter
          isPending={isPending}
          submitLabel={initial ? "Save Changes" : "Add Color"}
          onClose={onClose}
        />
      </form>
    </ModalShell>
  );
}

function PranaColorsTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
  const { data, isLoading, isError } = usePranaColors();
  const deleteColor = useDeleteColor();
  const [modal, setModal] = useState<PranaColor | null | "new">(null);
  const [deleteTarget, setDeleteTarget] = useState<PranaColor | null>(null);

  const canModify = (item: PranaColor) => isSuperAdmin || !!item.healerId;

  const columns: ColumnDef<PranaColor, unknown>[] = [
    {
      id: "swatch",
      header: "",
      cell: ({ row }) => <ColorSwatch hex={row.original.colorHex} />,
    },
    {
      accessorKey: "name",
      header: "Name",
      cell: (info) => (
        <span className="font-medium text-text">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "code",
      header: "Code",
      cell: (info) => (
        <span className="font-mono text-xs text-text-light">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "properties",
      header: "Properties",
      cell: (info) => {
        const props = (info.getValue() as string[] | undefined)?.join(", ");
        return (
          <span className="text-text-light max-w-xs truncate block">
            {props ? props.slice(0, 60) + (props.length > 60 ? "…" : "") : "—"}
          </span>
        );
      },
    },
    {
      accessorKey: "healerId",
      header: "Source",
      cell: ({ row }) => <SourceBadge healerId={row.original.healerId} />,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) =>
        canModify(row.original) ? (
          <RowActions
            onEdit={() => setModal(row.original)}
            onDelete={() => setDeleteTarget(row.original)}
            deleting={deleteColor.isPending}
          />
        ) : (
          <LockIcon />
        ),
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-text-light">
          System colors are seeded globally. Your own custom colors are also listed here.
        </p>
        <Button onClick={() => setModal("new")} className="px-3 py-2 h-auto shrink-0">
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">Add Color</span>
        </Button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load colors.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block">
            <DataTable<PranaColor>
              columns={columns}
              data={data}
              enableSorting={false}
              emptyMessage="No colors found."
            />
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.map((item) => (
              <div key={item.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <ColorSwatch hex={item.colorHex} />
                    <span className="font-semibold text-text truncate">{item.name}</span>
                    <SourceBadge healerId={item.healerId} />
                  </div>
                  {canModify(item) ? (
                    <RowActions
                      onEdit={() => setModal(item)}
                      onDelete={() => setDeleteTarget(item)}
                      deleting={deleteColor.isPending}
                    />
                  ) : (
                    <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                  )}
                </div>
                {item.properties && item.properties.length > 0 && (
                  <p className="text-xs text-text-light mt-1 line-clamp-2">
                    {item.properties.join(", ")}
                  </p>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {modal !== null && (
        <ColorModal initial={modal === "new" ? undefined : modal} onClose={() => setModal(null)} />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete prana color?"
        description={deleteTarget ? `"${deleteTarget.name}" will be removed.` : undefined}
        confirmLabel="Delete"
        destructive
        loading={deleteColor.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            deleteColor.mutate(deleteTarget.id, {
              onSettled: () => setDeleteTarget(null),
            });
          }
        }}
      />
    </div>
  );
}

// ── Chakras Tab ───────────────────────────────────────────────────────────────

function CategoryBadge({ category }: { category?: string | null }) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        category === "major" ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"
      )}
    >
      {category ?? "—"}
    </Badge>
  );
}

function ChakraModal({
  initial,
  onClose,
}: {
  initial?: Chakra;
  onClose: () => void;
}) {
  const [error, setError] = useState("");
  const create = useCreateChakra();
  const update = useUpdateChakra();

  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      code: initial?.code ?? "",
      category: initial?.category ?? "major",
      location: initial?.location ?? "",
    },
    onSubmit: async ({ value }) => {
      setError("");
      const payload: ChakraPayload = {
        name: value.name,
        code: value.code.toUpperCase(),
        category: value.category,
        location: value.location,
        healerId: initial ? initial.healerId : null,
      };
      try {
        if (initial) {
          await update.mutateAsync({ id: initial.id, payload });
        } else {
          await create.mutateAsync(payload);
        }
        onClose();
      } catch {
        setError("Failed to save. Please try again.");
      }
    },
  });

  const isPending = create.isPending || update.isPending;

  return (
    <ModalShell title={initial ? "Edit Chakra" : "Add Chakra"} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          form.handleSubmit();
        }}
        className="space-y-3"
      >
        <Field label="Name">
          <form.Field
            name="name"
            children={(field) => <TextField field={field} className={fieldCls} />}
          />
        </Field>
        <Field label="Code (e.g. CROWN)">
          <form.Field
            name="code"
            children={(field) => (
              <TextField field={field} disabled={!!initial} className={fieldCls} />
            )}
          />
        </Field>
        <Field label="Category">
          <form.Field
            name="category"
            children={(field) => (
              <SelectField
                field={field}
                options={[
                  { label: "Major", value: "major" },
                  { label: "Minor", value: "minor" },
                ]}
                className={fieldCls}
              />
            )}
          />
        </Field>
        <Field label="Location">
          <form.Field
            name="location"
            children={(field) => (
              <TextField field={field} placeholder="e.g. Top of the head" className={fieldCls} />
            )}
          />
        </Field>
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <ModalFooter
          isPending={isPending}
          submitLabel={initial ? "Save Changes" : "Add Chakra"}
          onClose={onClose}
        />
      </form>
    </ModalShell>
  );
}

function ChakrasTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
  const { data, isLoading, isError } = useChakras();
  const deleteChakra = useDeleteChakra();
  const [modal, setModal] = useState<Chakra | null | "new">(null);
  const [deleteTarget, setDeleteTarget] = useState<Chakra | null>(null);

  const canModify = (item: Chakra) => isSuperAdmin || !!item.healerId;

  const columns: ColumnDef<Chakra, unknown>[] = [
    {
      accessorKey: "name",
      header: "Name",
      cell: (info) => (
        <span className="font-medium text-text">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "code",
      header: "Code",
      cell: (info) => (
        <span className="font-mono text-xs text-text-light">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "category",
      header: "Category",
      cell: ({ row }) => <CategoryBadge category={row.original.category} />,
    },
    {
      accessorKey: "location",
      header: "Location",
      cell: (info) => (
        <span className="text-text-light text-xs max-w-xs truncate block">
          {(info.getValue() as string | null) ?? "—"}
        </span>
      ),
    },
    {
      accessorKey: "healerId",
      header: "Source",
      cell: ({ row }) => <SourceBadge healerId={row.original.healerId} />,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) =>
        canModify(row.original) ? (
          <RowActions
            onEdit={() => setModal(row.original)}
            onDelete={() => setDeleteTarget(row.original)}
            deleting={deleteChakra.isPending}
          />
        ) : (
          <LockIcon />
        ),
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-text-light">
          System chakras (26 seeded) are global. Add your own custom chakras below.
        </p>
        <Button onClick={() => setModal("new")} className="px-3 py-2 h-auto shrink-0">
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">Add Chakra</span>
        </Button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load chakras.</p>}

      {data && (
        <>
          {/* Desktop */}
          <div className="hidden md:block">
            <DataTable<Chakra>
              columns={columns}
              data={data}
              enableSorting={false}
              emptyMessage="No chakras found."
            />
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-3">
            {data.map((item) => (
              <div key={item.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <span className="font-semibold text-text">{item.name}</span>
                    <div className="flex items-center gap-2 mt-1">
                      <CategoryBadge category={item.category} />
                      <SourceBadge healerId={item.healerId} />
                    </div>
                  </div>
                  {canModify(item) ? (
                    <RowActions
                      onEdit={() => setModal(item)}
                      onDelete={() => setDeleteTarget(item)}
                      deleting={deleteChakra.isPending}
                    />
                  ) : (
                    <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                  )}
                </div>
                {item.location && <p className="text-xs text-text-light mt-1">{item.location}</p>}
              </div>
            ))}
          </div>
        </>
      )}

      {modal !== null && (
        <ChakraModal initial={modal === "new" ? undefined : modal} onClose={() => setModal(null)} />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete chakra?"
        description={deleteTarget ? `"${deleteTarget.name}" will be removed.` : undefined}
        confirmLabel="Delete"
        destructive
        loading={deleteChakra.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            deleteChakra.mutate(deleteTarget.id, {
              onSettled: () => setDeleteTarget(null),
            });
          }
        }}
      />
    </div>
  );
}

// ── Protocol Mappings Tab ─────────────────────────────────────────────────────

function UsageBadge({ usageType }: { usageType: string }) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        usageType === "CLEANSING"
          ? "bg-green-100 text-green-700"
          : "bg-orange-100 text-orange-700"
      )}
    >
      {usageType}
    </Badge>
  );
}

function MappingModal({ onClose, isSuperAdmin }: { onClose: () => void; isSuperAdmin: boolean }) {
  const { data: colors } = usePranaColors();
  const [usageType, setUsageType] = useState<"CLEANSING" | "ENERGISING">("CLEANSING");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState("");
  const create = useCreateMapping();

  const form = useForm({
    defaultValues: {
      protocolName: "",
      chakraName: "",
    },
    onSubmit: async ({ value }) => {
      if (selectedIds.size === 0) { setError("Select at least one color."); return; }
      setError("");
      try {
        await create.mutateAsync({
          protocolName: value.protocolName,
          colorIds: Array.from(selectedIds),
          usageType,
          healerId: null,
          chakraName: value.chakraName,
        });
        onClose();
      } catch {
        setError("Failed to save. Please try again.");
      }
    },
  });

  const toggle = (id: string) =>
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  return (
    <ModalShell title="Add Protocol Mapping" onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          form.handleSubmit();
        }}
        className="space-y-4"
      >
        <Field label="Protocol Name">
          <form.Field
            name="protocolName"
            children={(field) => (
              <TextField field={field} placeholder="e.g. STRESS_RELIEF" className={fieldCls} />
            )}
          />
        </Field>

        <Field label="Chakra Name">
          <form.Field
            name="chakraName"
            children={(field) => (
              <TextField field={field} placeholder="e.g. Crown Chakra" className={fieldCls} />
            )}
          />
        </Field>

        <Field label="Usage Type">
          <div className="flex gap-2">
            {(["CLEANSING", "ENERGISING"] as const).map((type) => (
              <Button
                key={type}
                type="button"
                variant="outline"
                onClick={() => setUsageType(type)}
                className={cn(
                  "flex-1 py-2 h-auto font-medium",
                  usageType === type
                    ? type === "CLEANSING"
                      ? "bg-green-100 border-green-400 text-green-700 hover:bg-green-100 hover:text-green-700"
                      : "bg-orange-100 border-orange-400 text-orange-700 hover:bg-orange-100 hover:text-orange-700"
                    : "text-text-light"
                )}
              >
                {type}
              </Button>
            ))}
          </div>
        </Field>

        <div>
          <Label className="block text-xs text-text-light mb-2">
            Prana Colors
            {selectedIds.size > 0 && (
              <span className="ml-1.5 text-primary font-medium">({selectedIds.size} selected)</span>
            )}
          </Label>
          <div className="flex flex-wrap gap-2 max-h-52 overflow-y-auto pr-1">
            {colors?.map((c) => {
              const selected = selectedIds.has(c.id);
              return (
                <Button
                  key={c.id}
                  type="button"
                  variant="outline"
                  size="xs"
                  onClick={() => toggle(c.id)}
                  className={cn(
                    "h-auto gap-1.5 px-2.5 py-1.5 rounded-full font-medium",
                    selected
                      ? "border-primary bg-primary/10 text-primary shadow-sm hover:bg-primary/10 hover:text-primary"
                      : "text-text-light hover:border-primary/50 hover:text-text"
                  )}
                >
                  <span
                    className="w-3 h-3 rounded-full flex-shrink-0 border border-black/10"
                    style={{ backgroundColor: c.colorHex ?? "#ccc" }}
                  />
                  {c.name}
                  {selected && <span className="text-primary">✓</span>}
                </Button>
              );
            })}
          </div>
        </div>

        {isSuperAdmin && (
          <p className="text-xs text-text-light">
            Mappings will be created as system-level (no healer restriction).
          </p>
        )}
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <ModalFooter
          isPending={create.isPending}
          submitLabel={
            selectedIds.size > 1 ? `Add ${selectedIds.size} Mappings` : "Add Mapping"
          }
          onClose={onClose}
        />
      </form>
    </ModalShell>
  );
}

type MappingItem = {
  id: string;
  protocolName: string;
  colorId: string;
  usageType: string;
  healerId?: string | null;
};

function ProtocolMappingsTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
  const { data: mappings, isLoading, isError } = useProtocolMappings();
  const { data: colors } = usePranaColors();
  const deleteMapping = useDeleteMapping();
  const [showModal, setShowModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<MappingItem | null>(null);

  const colorById = (id: string) => colors?.find((c) => c.id === id);
  const canDelete = (m: { healerId?: string | null }) => isSuperAdmin || !!m.healerId;

  const columns: ColumnDef<MappingItem, unknown>[] = [
    {
      accessorKey: "protocolName",
      header: "Protocol",
      cell: (info) => (
        <span className="font-medium text-text">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "colorId",
      header: "Color",
      cell: ({ row }) => {
        const color = colorById(row.original.colorId);
        return (
          <div className="flex items-center gap-2">
            <ColorSwatch hex={color?.colorHex} />
            <span className="text-text-light">{color?.name ?? row.original.colorId}</span>
          </div>
        );
      },
    },
    {
      accessorKey: "usageType",
      header: "Usage",
      cell: ({ row }) => <UsageBadge usageType={row.original.usageType} />,
    },
    {
      accessorKey: "healerId",
      header: "Source",
      cell: ({ row }) => <SourceBadge healerId={row.original.healerId} />,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) =>
        canDelete(row.original) ? (
          <RowActions
            onDelete={() => setDeleteTarget(row.original)}
            deleting={deleteMapping.isPending}
          />
        ) : (
          <LockIcon />
        ),
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-text-light">
          Maps a protocol name to cleansing or energising prana colors.
        </p>
        <Button onClick={() => setShowModal(true)} className="px-3 py-2 h-auto shrink-0">
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">Add Mapping</span>
        </Button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load mappings.</p>}

      {mappings && (
        <>
          {/* Desktop */}
          <div className="hidden md:block">
            <DataTable<MappingItem>
              columns={columns}
              data={mappings}
              enableSorting={false}
              emptyMessage="No mappings found."
            />
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-3">
            {mappings.map((m) => {
              const color = colorById(m.colorId);
              return (
                <div key={m.id} className="border border-border rounded-xl p-4">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="font-semibold text-text">{m.protocolName}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <ColorSwatch hex={color?.colorHex} />
                        <span className="text-xs text-text-light">{color?.name ?? "—"}</span>
                        <UsageBadge usageType={m.usageType} />
                      </div>
                    </div>
                    {canDelete(m) ? (
                      <RowActions
                        onDelete={() => setDeleteTarget(m)}
                        deleting={deleteMapping.isPending}
                      />
                    ) : (
                      <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}

      {showModal && (
        <MappingModal onClose={() => setShowModal(false)} isSuperAdmin={isSuperAdmin} />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete protocol mapping?"
        description={
          deleteTarget ? `The "${deleteTarget.protocolName}" mapping will be removed.` : undefined
        }
        confirmLabel="Delete"
        destructive
        loading={deleteMapping.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            deleteMapping.mutate(deleteTarget.id, {
              onSettled: () => setDeleteTarget(null),
            });
          }
        }}
      />
    </div>
  );
}

// ── Protocols Tab ─────────────────────────────────────────────────────────────

function ProtocolTypeBadge({ protocolType }: { protocolType: string }) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        protocolType === "PROTOCOL"
          ? "bg-blue-100 text-blue-700"
          : "bg-gray-100 text-gray-600"
      )}
    >
      {protocolType}
    </Badge>
  );
}

// ── Protocol Chakras & Colors (self-serve, per-protocol) ──────────────────────
// Lets a healer pick a protocol they can already see and add/edit/remove which
// chakras (and each chakra's cleansing/energising colors) it addresses,
// without a bulk xlsx upload. Additions are always the caller's own
// (healerId = current healer per V38) and editable/deletable only by them
// (or SUPADM) — system (bulk-imported) rows stay read-only for everyone else.

function ChakraColorRow({
  group,
  colors,
  canModify,
  onEdit,
  onDelete,
  deleting,
}: {
  group: ProtocolChakraColorGroup;
  colors: PranaColor[];
  canModify: boolean;
  onEdit: () => void;
  onDelete: () => void;
  deleting: boolean;
}) {
  const colorLabel = (id: string) => colors.find((c) => c.id === id)?.name ?? id;
  const colorHex = (id: string) => colors.find((c) => c.id === id)?.colorHex;

  const colorList = (ids: string[]) =>
    ids.length === 0 ? (
      <span className="text-text-light">—</span>
    ) : (
      <div className="flex flex-wrap gap-1.5 mt-0.5">
        {ids.map((id) => (
          <span key={id} className="inline-flex items-center gap-1 text-xs text-text-light">
            <ColorSwatch hex={colorHex(id)} />
            {colorLabel(id)}
          </span>
        ))}
      </div>
    );

  return (
    <div className="border border-border rounded-lg p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="font-medium text-text text-sm">{group.chakraName}</span>
          <SourceBadge healerId={group.healerId} />
        </div>
        {canModify ? (
          <RowActions onEdit={onEdit} onDelete={onDelete} deleting={deleting} />
        ) : (
          <LockIcon />
        )}
      </div>
      <div className="grid grid-cols-2 gap-3 mt-2 text-xs">
        <div>
          <span className="text-text-light font-medium">Cleansing</span>
          {colorList(group.cleansingColorIds)}
        </div>
        <div>
          <span className="text-text-light font-medium">Energising</span>
          {colorList(group.energisingColorIds)}
        </div>
      </div>
    </div>
  );
}

function ChakraColorForm({
  protocolId,
  editing,
  usedChakraIds,
  onDone,
}: {
  protocolId: string;
  /** null = adding a new chakra link; a group = editing that link's colors. */
  editing: ProtocolChakraColorGroup | null;
  /** Chakra ids already linked to this protocol (for the caller) — excluded from the add picker. */
  usedChakraIds: string[];
  onDone: () => void;
}) {
  const { data: chakras } = useChakras();
  const { data: colors } = usePranaColors();
  const addMutation = useAddProtocolChakraColors(protocolId);
  const updateMutation = useUpdateProtocolChakraColor(protocolId);

  const [chakraId, setChakraId] = useState(editing?.chakraId ?? "");
  const [cleansing, setCleansing] = useState<string[]>(editing?.cleansingColorIds ?? []);
  const [energising, setEnergising] = useState<string[]>(editing?.energisingColorIds ?? []);

  const chakraOptions = (chakras ?? []).filter(
    (c) => editing?.chakraId === c.id || !usedChakraIds.includes(c.id)
  );
  const colorIds = (colors ?? []).map((c) => c.id);
  const colorLabel = (id: string) => (colors ?? []).find((c) => c.id === id)?.name ?? id;

  const isPending = addMutation.isPending || updateMutation.isPending;

  const handleSave = async () => {
    if (!chakraId) return;
    try {
      if (editing) {
        await updateMutation.mutateAsync({
          protocolChakraId: editing.protocolChakraId,
          cleansingColorIds: cleansing,
          energisingColorIds: energising,
        });
      } else {
        await addMutation.mutateAsync([
          { chakraId, cleansingColorIds: cleansing, energisingColorIds: energising },
        ]);
      }
      onDone();
    } catch {
      // error toast already shown by the hook
    }
  };

  return (
    <div className="border border-dashed border-border rounded-lg p-3 space-y-2.5">
      <Field label="Chakra">
        <select
          value={chakraId}
          onChange={(e) => setChakraId(e.target.value)}
          disabled={!!editing}
          className={cn(fieldCls, "w-full rounded-md bg-background")}
        >
          <option value="">Select a chakra…</option>
          {chakraOptions.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Cleansing colors">
        <MultiSelect<string>
          title="Cleansing colors"
          options={colorIds}
          value={cleansing}
          onChange={setCleansing}
          getOptionKey={(id) => id}
          getOptionLabel={colorLabel}
          placeholder="Select colors"
        />
      </Field>
      <Field label="Energising colors">
        <MultiSelect<string>
          title="Energising colors"
          options={colorIds}
          value={energising}
          onChange={setEnergising}
          getOptionKey={(id) => id}
          getOptionLabel={colorLabel}
          placeholder="Select colors"
        />
      </Field>
      <div className="flex gap-2">
        <Button
          type="button"
          disabled={!chakraId || isPending}
          onClick={handleSave}
          className="flex-1 py-2 h-auto text-sm"
        >
          {isPending ? "Saving…" : "Save"}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={onDone}
          className="px-3 py-2 h-auto text-sm font-normal text-text"
        >
          Cancel
        </Button>
      </div>
    </div>
  );
}

function ChakraColorSection({
  protocolId,
  isSuperAdmin,
  currentHealerId,
}: {
  protocolId: string;
  isSuperAdmin: boolean;
  currentHealerId?: string;
}) {
  const { data: groups, isLoading } = useChakraColorGroupsForProtocol(protocolId);
  const { data: colors } = usePranaColors();
  const deleteMutation = useDeleteProtocolChakraColor(protocolId);
  const [editing, setEditing] = useState<ProtocolChakraColorGroup | "new" | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProtocolChakraColorGroup | null>(null);

  const canModify = (g: ProtocolChakraColorGroup) =>
    isSuperAdmin || (!!g.healerId && g.healerId === currentHealerId);

  return (
    <div className="border-t border-border pt-4 mt-4 space-y-3">
      <div className="flex items-center justify-between">
        <Label className="text-sm font-semibold text-text">Chakras &amp; Colors</Label>
        {editing === null && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => setEditing("new")}
            className="h-auto py-1.5 px-2.5 text-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            Add Chakra
          </Button>
        )}
      </div>

      {isLoading && <p className="text-xs text-text-light">Loading…</p>}
      {!isLoading && (groups ?? []).length === 0 && editing === null && (
        <p className="text-xs text-text-light">No chakras linked yet. Add one below.</p>
      )}

      <div className="space-y-2">
        {(groups ?? []).map((g) =>
          editing !== "new" && editing?.protocolChakraId === g.protocolChakraId ? (
            <ChakraColorForm
              key={g.protocolChakraId}
              protocolId={protocolId}
              editing={editing}
              usedChakraIds={[]}
              onDone={() => setEditing(null)}
            />
          ) : (
            <ChakraColorRow
              key={g.protocolChakraId}
              group={g}
              colors={colors ?? []}
              canModify={canModify(g)}
              onEdit={() => setEditing(g)}
              onDelete={() => setDeleteTarget(g)}
              deleting={deleteMutation.isPending}
            />
          )
        )}
      </div>

      {editing === "new" && (
        <ChakraColorForm
          protocolId={protocolId}
          editing={null}
          usedChakraIds={(groups ?? []).map((g) => g.chakraId)}
          onDone={() => setEditing(null)}
        />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Remove chakra from protocol?"
        description={
          deleteTarget ? `"${deleteTarget.chakraName}" and its colors will be removed.` : undefined
        }
        confirmLabel="Remove"
        destructive
        loading={deleteMutation.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget.protocolChakraId, {
              onSettled: () => setDeleteTarget(null),
            });
          }
        }}
      />
    </div>
  );
}

function ProtocolModal({
  initial,
  isSuperAdmin,
  onClose,
}: {
  initial?: Protocol;
  isSuperAdmin: boolean;
  onClose: () => void;
}) {
  const [error, setError] = useState("");
  // Tracks the protocol being edited, including the one just created in this same modal
  // session — a fresh "Add Protocol" has no id yet, so chakras/colors can't attach to
  // anything; once create succeeds, this flips the modal into edit mode in place (see
  // onSubmit below) instead of closing, so the Chakras & Colors section can appear.
  const [protocol, setProtocol] = useState<Protocol | undefined>(initial);
  const create = useCreateProtocol();
  const update = useUpdateProtocol();
  const { user } = useAuth();

  const form = useForm({
    defaultValues: {
      name: protocol?.name ?? "",
      sourceBook: protocol?.sourceBook ?? "",
      pageNumber: protocol?.pageNumber?.toString() ?? "",
      category: protocol?.category ?? "",
      description: protocol?.description ?? "",
      chapter: protocol?.chapter ?? "",
      protocolType: protocol?.protocolType ?? "PROTOCOL",
    },
    onSubmit: async ({ value }) => {
      setError("");
      const pageNumber = parseInt(value.pageNumber, 10);
      if (!value.sourceBook.trim() || Number.isNaN(pageNumber)) {
        setError("Book and Page number are required.");
        return;
      }
      const payload: ProtocolPayload = {
        name: value.name,
        sourceBook: value.sourceBook,
        pageNumber,
        category: value.category,
        description: value.description,
        chapter: value.chapter,
        protocolType: value.protocolType as "PROTOCOL" | "SUPPLEMENTARY",
        healerId: protocol ? protocol.healerId : null,
      };
      try {
        if (protocol) {
          const updated = await update.mutateAsync({ id: protocol.id, payload });
          setProtocol(updated);
          onClose();
        } else {
          const created = await create.mutateAsync(payload);
          setProtocol(created);
        }
      } catch {
        setError("Failed to save. Please try again.");
      }
    },
  });

  const isPending = create.isPending || update.isPending;

  return (
    <ModalShell title={protocol ? "Edit Protocol" : "Add Protocol"} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          form.handleSubmit();
        }}
        className="space-y-3"
      >
        <Field label="Protocol/Technique Name">
          <form.Field
            name="name"
            children={(field) => <TextField field={field} className={fieldCls} />}
          />
        </Field>
        <Field label="Book">
          <form.Field
            name="sourceBook"
            children={(field) => (
              <TextField field={field} placeholder="e.g. Advanced Pranic Healing" className={fieldCls} />
            )}
          />
        </Field>
        <Field label="Page">
          <form.Field
            name="pageNumber"
            children={(field) => (
              <TextField field={field} type="number" className={fieldCls} />
            )}
          />
        </Field>
        <Field label="Type">
          <form.Field
            name="protocolType"
            children={(field) => (
              <SelectField
                field={field}
                options={[
                  { label: "Protocol", value: "PROTOCOL" },
                  { label: "Supplementary", value: "SUPPLEMENTARY" },
                ]}
                className={fieldCls}
              />
            )}
          />
        </Field>
        <Field label="Category">
          <form.Field
            name="category"
            children={(field) => <TextField field={field} className={fieldCls} />}
          />
        </Field>
        <Field label="Chapter">
          <form.Field
            name="chapter"
            children={(field) => <TextField field={field} className={fieldCls} />}
          />
        </Field>
        <Field label="Description">
          <form.Field
            name="description"
            children={(field) => (
              <TextareaField field={field} className={cn(fieldCls, "p-2.5")} />
            )}
          />
        </Field>
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <ModalFooter
          isPending={isPending}
          submitLabel={protocol ? "Save Changes" : "Add Protocol"}
          onClose={onClose}
        />
        {!initial && protocol && (
          <p className="text-xs text-text-light text-center pt-1">
            Protocol created — add chakras &amp; colors below, then close when done.
          </p>
        )}
      </form>

      {/* Sibling to the form (not nested inside it) — its Save/Cancel buttons must never be
          swept into the protocol name/book/page form's submit handler. Only shown once the
          protocol exists (either passed in for editing, or just created above): chakras/colors
          attach to a protocol id, so there's nothing to manage yet before that. */}
      {protocol && (
        <ChakraColorSection
          protocolId={protocol.id}
          isSuperAdmin={isSuperAdmin}
          currentHealerId={user?.id}
        />
      )}
    </ModalShell>
  );
}

function ImportProtocolsDialog({
  onClose,
  onSubmitted,
}: {
  onClose: () => void;
  /** Called after a successful submit, before closing — lets the caller switch to the Imports tab. */
  onSubmitted: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const submitImport = useSubmitProtocolImport();
  // submitImport.isPending only disables the button once React re-renders — two clicks fired
  // back-to-back (a real double-click) can both fire before that lands, submitting the same
  // file twice. This ref is checked/set synchronously, so the second click is dropped
  // regardless of render timing.
  const isSubmittingRef = useRef(false);

  const handleUpload = async () => {
    if (!file || isSubmittingRef.current) return;
    isSubmittingRef.current = true;
    try {
      await submitImport.mutateAsync(file);
      onSubmitted();
      onClose();
    } catch {
      // error toast already shown by the hook
    } finally {
      isSubmittingRef.current = false;
    }
  };

  return (
    <ModalShell title="Import Protocols" onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-text-light">
          Upload a .xlsx or .csv file with columns: Protocol/Technique Name, Book,
          Page, Category, Description, Chapter, Type.
        </p>
        <p className="text-sm text-text-light">
          Optionally also include Chakras Used, Cleansing Color and Energizing
          Color columns to link the chakras and colors each protocol uses —
          unrecognized chakra/color names are created automatically. This never
          creates or edits a protocol: a row whose Protocol/Technique Name +
          Page doesn't already match an existing protocol is skipped.
        </p>
        <p className="text-sm text-text-light">
          The import runs in the background — you can keep working, submit
          another import, and check progress any time in the Imports tab.
        </p>
        <Input
          ref={fileInputRef}
          type="file"
          accept=".xlsx,.csv"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <div className="flex gap-2 pt-1">
          <Button
            type="button"
            disabled={!file || submitImport.isPending}
            onClick={handleUpload}
            className="flex-1 py-2.5 h-auto"
          >
            {submitImport.isPending ? "Submitting…" : "Upload"}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            className="px-4 py-2.5 h-auto font-normal text-text"
          >
            Cancel
          </Button>
        </div>
      </div>
    </ModalShell>
  );
}

// ── Imports Tab ────────────────────────────────────────────────────────────────

function ImportJobStatusBadge({ status }: { status: ProtocolImportJobView["status"] }) {
  const styles: Record<ProtocolImportJobView["status"], string> = {
    QUEUED: "bg-gray-100 text-gray-600",
    PROCESSING: "bg-blue-100 text-blue-700",
    COMPLETED: "bg-green-100 text-green-700",
    FAILED: "bg-red-100 text-red-700",
  };
  return <Badge className={cn("font-semibold px-2", styles[status])}>{status}</Badge>;
}

function ImportJobRow({ job }: { job: ProtocolImportJobView }) {
  const [showErrors, setShowErrors] = useState(false);
  return (
    <div className="border border-border rounded-xl p-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium text-text truncate">{job.originalFilename ?? "—"}</p>
          <p className="text-xs text-text-light">{new Date(job.createdAt).toLocaleString()}</p>
        </div>
        <ImportJobStatusBadge status={job.status} />
      </div>
      {job.status === "COMPLETED" && (
        <p className="text-sm text-text-light">
          {job.totalRows} rows — {job.created} created, {job.updated} updated, {job.skipped} skipped
        </p>
      )}
      {job.status === "FAILED" && job.failureMessage && (
        <p className="text-sm text-red-500">{job.failureMessage}</p>
      )}
      {job.errors.length > 0 && (
        <div>
          <button
            type="button"
            onClick={() => setShowErrors((v) => !v)}
            className="text-xs text-primary underline"
          >
            {showErrors ? "Hide" : "Show"} {job.errors.length} row error{job.errors.length === 1 ? "" : "s"}
          </button>
          {showErrors && (
            <div className="max-h-48 overflow-y-auto border border-border rounded-lg p-2 mt-2 space-y-1">
              {job.errors.map((err, i) => (
                <p key={i} className="text-xs text-red-500">{err}</p>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ImportsTab() {
  const { data, isLoading, isError } = useProtocolImportJobs();

  return (
    <div>
      <p className="text-sm text-text-light mb-4">
        Every protocol import you've submitted, newest first. Still-running imports
        refresh automatically.
      </p>
      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load import jobs.</p>}
      {data && data.length === 0 && (
        <p className="text-text-light text-sm">
          No imports submitted yet — use Import on the Protocols tab.
        </p>
      )}
      {data && data.length > 0 && (
        <div className="space-y-3">
          {data.map((job) => (
            <ImportJobRow key={job.id} job={job} />
          ))}
        </div>
      )}
    </div>
  );
}

function ProtocolsTab({
  isSuperAdmin,
  onNavigateToImports,
}: {
  isSuperAdmin: boolean;
  onNavigateToImports: () => void;
}) {
  const { data, isLoading, isError } = useProtocols();
  const deleteProtocol = useDeleteProtocol();
  const [modal, setModal] = useState<Protocol | null | "new">(null);
  const [deleteTarget, setDeleteTarget] = useState<Protocol | null>(null);
  const [showImport, setShowImport] = useState(false);
  const [search, setSearch] = useState("");

  const canModify = (item: Protocol) => isSuperAdmin || !!item.healerId;

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return data ?? [];
    return (data ?? []).filter((p) =>
      [p.name, p.sourceBook, p.chapter]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(query))
    );
  }, [data, search]);

  const columns: ColumnDef<Protocol, unknown>[] = [
    {
      accessorKey: "name",
      header: "Name",
      cell: (info) => (
        <span className="font-medium text-text">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "sourceBook",
      header: "Book",
      cell: (info) => (
        <span className="text-text-light text-xs max-w-xs truncate block">
          {(info.getValue() as string | null) ?? "—"}
        </span>
      ),
    },
    {
      accessorKey: "pageNumber",
      header: "Page",
    },
    {
      accessorKey: "protocolType",
      header: "Type",
      cell: ({ row }) => <ProtocolTypeBadge protocolType={row.original.protocolType} />,
    },
    {
      accessorKey: "category",
      header: "Category",
      cell: (info) => (
        <span className="text-text-light text-xs">{(info.getValue() as string) ?? "—"}</span>
      ),
    },
    {
      accessorKey: "healerId",
      header: "Source",
      cell: ({ row }) => <SourceBadge healerId={row.original.healerId} />,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) =>
        canModify(row.original) ? (
          <RowActions
            onEdit={() => setModal(row.original)}
            onDelete={() => setDeleteTarget(row.original)}
            deleting={deleteProtocol.isPending}
          />
        ) : (
          <LockIcon />
        ),
    },
  ];

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
        <p className="text-sm text-text-light">
          Bulk-imported protocol catalog. Only "Protocol" type entries appear in the
          session form's dropdown.
        </p>
        <div className="flex gap-2 shrink-0">
          <Button
            variant="outline"
            onClick={() => setShowImport(true)}
            className="px-3 py-2 h-auto"
          >
            <Upload className="w-4 h-4" />
            <span className="hidden sm:inline">Import</span>
          </Button>
          <Button onClick={() => setModal("new")} className="px-3 py-2 h-auto shrink-0">
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">Add Protocol</span>
          </Button>
        </div>
      </div>

      <Input
        placeholder="Search by name, book, or chapter…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="mb-4 max-w-sm"
      />

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load protocols.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block">
            <DataTable<Protocol>
              columns={columns}
              data={filtered}
              enableSorting={false}
              emptyMessage="No protocols found."
            />
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {filtered.map((item) => (
              <div key={item.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <div className="min-w-0">
                    <span className="font-semibold text-text truncate block">
                      {item.name}
                    </span>
                    <div className="flex items-center gap-2 mt-1 flex-wrap">
                      <ProtocolTypeBadge protocolType={item.protocolType} />
                      <SourceBadge healerId={item.healerId} />
                    </div>
                  </div>
                  {canModify(item) ? (
                    <RowActions
                      onEdit={() => setModal(item)}
                      onDelete={() => setDeleteTarget(item)}
                      deleting={deleteProtocol.isPending}
                    />
                  ) : (
                    <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                  )}
                </div>
                <p className="text-xs text-text-light">
                  {item.sourceBook ?? "—"}, p.{item.pageNumber}
                </p>
              </div>
            ))}
          </div>
        </>
      )}

      {modal !== null && (
        <ProtocolModal
          initial={modal === "new" ? undefined : modal}
          isSuperAdmin={isSuperAdmin}
          onClose={() => setModal(null)}
        />
      )}

      {showImport && (
        <ImportProtocolsDialog
          onClose={() => setShowImport(false)}
          onSubmitted={onNavigateToImports}
        />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete protocol?"
        description={deleteTarget ? `"${deleteTarget.name}" will be removed.` : undefined}
        confirmLabel="Delete"
        destructive
        loading={deleteProtocol.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            deleteProtocol.mutate(deleteTarget.id, {
              onSettled: () => setDeleteTarget(null),
            });
          }
        }}
      />
    </div>
  );
}

// ── Bulk wipe (SUPADM only) ─────────────────────────────────────────────────────

const WIPE_TYPE_OPTIONS: { label: string; value: MasterDataType }[] = [
  { label: "All", value: "ALL" },
  { label: "Prana Colors", value: "COLORS" },
  { label: "Chakras", value: "CHAKRAS" },
  // { label: "Protocol Mappings", value: "MAPPINGS" },
  { label: "Protocols", value: "PROTOCOLS" },
];

/** Rendered only for SUPADM by the caller — the backend also refuses everyone else regardless. */
function WipeMasterDataControl() {
  const [type, setType] = useState<MasterDataType>("ALL");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const wipe = useWipeMasterData();

  const selectedLabel = WIPE_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type;

  return (
    <div className="mb-6 border border-red-200 bg-red-50 rounded-lg p-3 flex flex-wrap items-center gap-3">
      <span className="text-sm text-red-700 font-medium">Danger zone —</span>
      <select
        value={type}
        onChange={(e) => setType(e.target.value as MasterDataType)}
        className={cn(fieldCls, "rounded-md bg-background")}
      >
        {WIPE_TYPE_OPTIONS.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <Button
        type="button"
        variant="outline"
        className="border-red-300 text-red-700 hover:bg-red-100"
        onClick={() => setConfirmOpen(true)}
      >
        Delete master data
      </Button>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={type === "ALL" ? "Delete ALL master data?" : `Delete all ${selectedLabel}?`}
        description={`This permanently removes every ${
          type === "ALL" ? "prana color, chakra, protocol mapping, and protocol" : selectedLabel.toLowerCase()
        } row — system and healer-owned alike. This cannot be undone from the UI.`}
        confirmLabel="Delete"
        destructive
        loading={wipe.isPending}
        onConfirm={() => {
          wipe.mutate(type, { onSettled: () => setConfirmOpen(false) });
        }}
      />
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

type Tab = "colors" | "chakras" | "mappings" | "protocols" | "imports";

export default function MasterDataPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "SUPADM";
  const [activeTab, setActiveTab] = useState<Tab>("colors");

  const tabs: { key: Tab; label: string }[] = [
    { key: "colors", label: "Prana Colors" },
    { key: "chakras", label: "Chakras" },
    // { key: "mappings", label: "Protocol Mappings" },
    { key: "protocols", label: "Protocols" },
    { key: "imports", label: "Imports" },
  ];

  const { isNewView } = useUiVersion();
  const body = (
    <>
      {activeTab === "colors" && <PranaColorsTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "chakras" && <ChakrasTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "mappings" && <ProtocolMappingsTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "protocols" && (
        <ProtocolsTab
          isSuperAdmin={isSuperAdmin}
          onNavigateToImports={() => setActiveTab("imports")}
        />
      )}
      {activeTab === "imports" && <ImportsTab />}
    </>
  );

  if (isNewView) {
    return (
      <div className="hlx">
        <div className="page-head">
          <div>
            <h1>Master data</h1>
            <p className="muted" style={{ marginTop: 6 }}>Colours, chakras and protocols used in every case.</p>
          </div>
        </div>
        {isSuperAdmin && <WipeMasterDataControl />}
        <div className="tabs glass" role="tablist" aria-label="Master data">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={activeTab === tab.key}
              className={activeTab === tab.key ? "on" : ""}
              onClick={() => setActiveTab(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="hl-mdbody">{body}</div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl md:text-2xl font-bold text-text">Master Data</h1>
        <p className="text-sm text-text-light mt-0.5">
          Manage prana colors, chakras, and protocol color mappings.
        </p>
      </div>

      {isSuperAdmin && <WipeMasterDataControl />}

      {/* Tab bar */}
      <div className="flex border-b border-border mb-6 overflow-x-auto">
        {tabs.map((tab) => (
          <Button
            key={tab.key}
            type="button"
            variant="ghost"
            onClick={() => setActiveTab(tab.key)}
            className={cn(
              "px-4 py-2.5 h-auto rounded-none whitespace-nowrap border-b-2 -mb-px hover:bg-transparent",
              activeTab === tab.key
                ? "border-b-primary text-primary hover:text-primary"
                : "border-b-transparent text-text-light hover:text-text"
            )}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {activeTab === "colors" && <PranaColorsTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "chakras" && <ChakrasTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "mappings" && <ProtocolMappingsTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "protocols" && (
        <ProtocolsTab
          isSuperAdmin={isSuperAdmin}
          onNavigateToImports={() => setActiveTab("imports")}
        />
      )}
      {activeTab === "imports" && <ImportsTab />}
    </div>
  );
}
