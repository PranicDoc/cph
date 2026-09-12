import { useMemo, useRef, useState } from "react";
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
  useImportMasterData,
  type PranaColor,
  type Chakra,
  type Protocol,
  type PranaColorPayload,
  type ChakraPayload,
  type ProtocolPayload,
  type ImportResult,
  type MasterDataImportType,
} from "../../hooks/admin/useMasterData";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { DataTable } from "@/components/ui/data-table";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { SelectField, TextField, TextareaField } from "@/components/form/Field";
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
  const [showImport, setShowImport] = useState(false);

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
            <span className="hidden sm:inline">Add Color</span>
          </Button>
        </div>
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

      {showImport && (
        <ImportDialog
          type="color"
          columnHint="Code, Name, Type, Value, Color Hex, Description"
          onClose={() => setShowImport(false)}
        />
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
  const [showImport, setShowImport] = useState(false);

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
            <span className="hidden sm:inline">Add Chakra</span>
          </Button>
        </div>
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

      {showImport && (
        <ImportDialog
          type="chakra"
          columnHint="Code, Name, Category, Location"
          onClose={() => setShowImport(false)}
        />
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
  const [showImport, setShowImport] = useState(false);
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
        <div className="flex gap-2 shrink-0">
          <Button
            variant="outline"
            onClick={() => setShowImport(true)}
            className="px-3 py-2 h-auto"
          >
            <Upload className="w-4 h-4" />
            <span className="hidden sm:inline">Import</span>
          </Button>
          <Button onClick={() => setShowModal(true)} className="px-3 py-2 h-auto shrink-0">
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">Add Mapping</span>
          </Button>
        </div>
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

      {showImport && (
        <ImportDialog
          type="mapping"
          columnHint="Protocol Name, Color Code, Usage Type, Chakra Name"
          onClose={() => setShowImport(false)}
        />
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

function ProtocolModal({
  initial,
  onClose,
}: {
  initial?: Protocol;
  onClose: () => void;
}) {
  const [error, setError] = useState("");
  const create = useCreateProtocol();
  const update = useUpdateProtocol();

  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      sourceBook: initial?.sourceBook ?? "",
      pageNumber: initial?.pageNumber?.toString() ?? "",
      category: initial?.category ?? "",
      description: initial?.description ?? "",
      chapter: initial?.chapter ?? "",
      protocolType: initial?.protocolType ?? "PROTOCOL",
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
    <ModalShell title={initial ? "Edit Protocol" : "Add Protocol"} onClose={onClose}>
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
          submitLabel={initial ? "Save Changes" : "Add Protocol"}
          onClose={onClose}
        />
      </form>
    </ModalShell>
  );
}

function ImportDialog({
  type,
  columnHint,
  onClose,
}: {
  type: MasterDataImportType;
  columnHint: string;
  onClose: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const importMasterData = useImportMasterData(type);

  const handleUpload = async () => {
    if (!file) return;
    try {
      const res = await importMasterData.mutateAsync(file);
      setResult(res);
    } catch {
      // error toast already shown by the hook
    }
  };

  return (
    <ModalShell title="Import" onClose={onClose}>
      {!result ? (
        <div className="space-y-4">
          <p className="text-sm text-text-light">
            Upload a .xlsx or .csv file with columns: {columnHint}.
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
              disabled={!file || importMasterData.isPending}
              onClick={handleUpload}
              className="flex-1 py-2.5 h-auto"
            >
              {importMasterData.isPending ? "Uploading…" : "Upload"}
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
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-text">
            {result.totalRows} rows processed — {result.created} created,{" "}
            {result.updated} updated, {result.skipped} skipped.
          </p>
          {result.errors.length > 0 && (
            <div className="max-h-48 overflow-y-auto border border-border rounded-lg p-2 space-y-1">
              {result.errors.map((err, i) => (
                <p key={i} className="text-xs text-red-500">
                  {err}
                </p>
              ))}
            </div>
          )}
          <Button onClick={onClose} className="w-full py-2.5 h-auto">
            Done
          </Button>
        </div>
      )}
    </ModalShell>
  );
}

function ProtocolsTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
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
        <ProtocolModal initial={modal === "new" ? undefined : modal} onClose={() => setModal(null)} />
      )}

      {showImport && (
        <ImportDialog
          type="protocol"
          columnHint="Protocol/Technique Name, Book, Page, Category, Description, Chapter, Type"
          onClose={() => setShowImport(false)}
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

// ── Page ──────────────────────────────────────────────────────────────────────

type Tab = "colors" | "chakras" | "mappings" | "protocols";

export default function MasterDataPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "SUPADM";
  const [activeTab, setActiveTab] = useState<Tab>("colors");

  const tabs: { key: Tab; label: string }[] = [
    { key: "colors", label: "Prana Colors" },
    { key: "chakras", label: "Chakras" },
    { key: "mappings", label: "Protocol Mappings" },
    { key: "protocols", label: "Protocols" },
  ];

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl md:text-2xl font-bold text-text">Master Data</h1>
        <p className="text-sm text-text-light mt-0.5">
          Manage prana colors, chakras, and protocol color mappings.
        </p>
      </div>

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
      {activeTab === "protocols" && <ProtocolsTab isSuperAdmin={isSuperAdmin} />}
    </div>
  );
}
