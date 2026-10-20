import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import type { ColumnDef } from "@tanstack/react-table";
import {
  useAdminPackages,
  useCreatePackage,
  useUpdatePackage,
  useDeactivatePackage,
} from "../../hooks/admin/useAdminPackages";
import type { AdminPackage, CreatePackagePayload } from "../../hooks/admin/useAdminPackages";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/common/StatusBadge";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { TextField } from "@/components/form/Field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { VALIDITY_UNITS, formatValidity, type ValidityUnit } from "../../lib/validity";

const EMPTY_FORM: CreatePackagePayload = { name: "", credits: 0, price_paise: 0, currency: "INR", display_order: 0 };

function PackageModal({ initial, onClose }: { initial?: AdminPackage; onClose: () => void }) {
  const [error, setError] = useState("");
  const create = useCreatePackage();
  const update = useUpdatePackage();

  const form = useForm({
    defaultValues: initial
      ? {
          name: initial.name,
          credits: String(initial.credits),
          price_paise: String(initial.price_paise),
          currency: initial.currency ?? "INR",
          display_order: String(initial.display_order),
          validity_value: String(initial.validity_value ?? 0),
          validity_unit: (initial.validity_unit ?? "DAYS") as ValidityUnit,
        }
      : {
          name: EMPTY_FORM.name,
          credits: String(EMPTY_FORM.credits),
          price_paise: String(EMPTY_FORM.price_paise),
          currency: EMPTY_FORM.currency ?? "INR",
          display_order: String(EMPTY_FORM.display_order),
          validity_value: "0",
          validity_unit: "DAYS" as ValidityUnit,
        },
    onSubmit: async ({ value }) => {
      setError("");
      const payload: CreatePackagePayload = {
        name: value.name,
        credits: parseInt(value.credits, 10) || 0,
        price_paise: parseInt(value.price_paise, 10) || 0,
        currency: value.currency,
        display_order: parseInt(value.display_order, 10) || 0,
        validity_value: parseInt(value.validity_value, 10) || 0,
        validity_unit: value.validity_unit,
      };
      try {
        initial
          ? await update.mutateAsync({ id: initial.id, payload })
          : await create.mutateAsync(payload);
        onClose();
      } catch {
        setError("Failed to save package.");
      }
    },
  });

  const isPending = create.isPending || update.isPending;

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 gap-0 max-h-[90vh] overflow-y-auto max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        <DialogTitle className="text-lg font-bold text-text mb-4">
          {initial ? "Edit Package" : "New Package"}
        </DialogTitle>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
          className="space-y-3"
        >
          <div>
            <Label className="block text-xs text-text-light mb-1">Name</Label>
            <form.Field
              name="name"
              children={(field) => (
                <TextField field={field} className="border px-3 py-2.5" />
              )}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">Credits</Label>
              <form.Field
                name="credits"
                children={(field) => (
                  <TextField field={field} type="number" className="border px-3 py-2.5" />
                )}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">Price (paise)</Label>
              <form.Field
                name="price_paise"
                children={(field) => (
                  <>
                    <TextField field={field} type="number" className="border px-3 py-2.5" />
                    <p className="text-xs text-text-light mt-0.5">
                      ₹{((parseInt(field.state.value, 10) || 0) / 100).toFixed(2)}
                    </p>
                  </>
                )}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">Currency</Label>
              <form.Field
                name="currency"
                children={(field) => (
                  <TextField field={field} className="border px-3 py-2.5" />
                )}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">Display Order</Label>
              <form.Field
                name="display_order"
                children={(field) => (
                  <TextField field={field} type="number" className="border px-3 py-2.5" />
                )}
              />
            </div>
          </div>
          <div>
            <Label className="block text-xs text-text-light mb-1">Plan validity</Label>
            <div className="grid grid-cols-2 gap-3">
              <form.Field
                name="validity_value"
                children={(field) => (
                  <TextField field={field} type="number" className="border px-3 py-2.5" />
                )}
              />
              <form.Field
                name="validity_unit"
                children={(field) => (
                  <Select
                    value={field.state.value}
                    onValueChange={(v) => field.handleChange(v as ValidityUnit)}
                  >
                    <SelectTrigger className="h-auto border px-3 py-2.5">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {VALIDITY_UNITS.map((u) => (
                        <SelectItem key={u.value} value={u.value}>
                          {u.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <p className="text-xs text-text-light mt-0.5">
              How long a purchase stays active; 0 means it never expires by date. Unused paid credits are
              forfeited when it ends. Applies to new purchases only.
            </p>
          </div>
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <div className="flex gap-2 pt-1">
            <Button type="submit" disabled={isPending} className="flex-1 py-2.5 h-auto">
              {isPending ? "Saving…" : initial ? "Save Changes" : "Create Package"}
            </Button>
            <Button type="button" variant="outline" onClick={onClose} className="px-4 py-2.5 h-auto font-normal text-text">
              Cancel
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export default function PackagesPage() {
  const { data, isLoading, isError } = useAdminPackages();
  const deactivate = useDeactivatePackage();
  const [modalPkg, setModalPkg] = useState<AdminPackage | null | "new">(null);
  const [deactivatePkg, setDeactivatePkg] = useState<AdminPackage | null>(null);

  const columns: ColumnDef<AdminPackage, unknown>[] = [
    {
      accessorKey: "name",
      header: "Name",
      cell: (info) => (
        <span className="font-semibold text-text">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "credits",
      header: () => <div className="text-right">Credits</div>,
      cell: (info) => <div className="text-right">{Number(info.getValue())}</div>,
    },
    {
      accessorKey: "price_paise",
      header: () => <div className="text-right">Price</div>,
      cell: (info) => (
        <div className="text-right font-mono">
          ₹{(Number(info.getValue()) / 100).toFixed(0)}
        </div>
      ),
    },
    {
      accessorKey: "currency",
      header: "Currency",
      cell: (info) => (
        <span className="text-text-light">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "display_order",
      header: () => <div className="text-right">Order</div>,
      cell: (info) => <div className="text-right">{Number(info.getValue())}</div>,
    },
    {
      id: "validity",
      header: "Validity",
      cell: ({ row }) => (
        <span className="text-text-light">
          {formatValidity(row.original.validity_value, row.original.validity_unit)}
        </span>
      ),
    },
    {
      accessorKey: "active",
      header: "Status",
      cell: (info) => <StatusBadge active={Boolean(info.getValue())} />,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => {
        const pkg = row.original;
        return (
          <div className="flex gap-1 justify-end">
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setModalPkg(pkg)}
              className="h-auto w-auto p-1.5 text-text-light"
            >
              <Pencil className="w-4 h-4" />
            </Button>
            {pkg.active && (
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => setDeactivatePkg(pkg)}
                disabled={deactivate.isPending}
                className="h-auto w-auto p-1.5 hover:bg-red-50 text-red-400 hover:text-red-400"
              >
                <Trash2 className="w-4 h-4" />
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div>
      <div className="flex items-start justify-between mb-4 gap-3">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-text">Credit Packages</h1>
          <p className="text-sm text-text-light mt-0.5">Manage packages available to healers and clinics.</p>
        </div>
        <Button onClick={() => setModalPkg("new")} className="px-3 py-2 md:px-4 h-auto shrink-0">
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">New Package</span>
          <span className="sm:hidden">New</span>
        </Button>
      </div>

      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load packages.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block">
            <DataTable<AdminPackage>
              columns={columns}
              data={data}
              enableSorting={false}
              emptyMessage="No packages found."
            />
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.map((pkg) => (
              <div key={pkg.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-text">{pkg.name}</span>
                    <StatusBadge active={pkg.active} />
                  </div>
                  <div className="flex gap-1">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => setModalPkg(pkg)}
                      className="h-auto w-auto p-1.5 text-text-light"
                    >
                      <Pencil className="w-4 h-4" />
                    </Button>
                    {pkg.active && (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => setDeactivatePkg(pkg)}
                        disabled={deactivate.isPending}
                        className="h-auto w-auto p-1.5 hover:bg-red-50 text-red-400 hover:text-red-400"
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    )}
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2 text-xs mt-3">
                  <div className="bg-muted/50 rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Credits</div>
                    <div className="font-semibold text-text">{pkg.credits}</div>
                  </div>
                  <div className="bg-muted/50 rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Price</div>
                    <div className="font-semibold text-text font-mono">₹{(pkg.price_paise / 100).toFixed(0)}</div>
                  </div>
                  <div className="bg-muted/50 rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Order</div>
                    <div className="font-semibold text-text">{pkg.display_order}</div>
                  </div>
                </div>
                <p className="text-xs text-text-light mt-2">
                  Validity: {formatValidity(pkg.validity_value, pkg.validity_unit)}
                </p>
              </div>
            ))}
          </div>
        </>
      )}

      {modalPkg !== null && (
        <PackageModal initial={modalPkg === "new" ? undefined : modalPkg} onClose={() => setModalPkg(null)} />
      )}

      <ConfirmDialog
        open={deactivatePkg !== null}
        onOpenChange={(open) => !open && setDeactivatePkg(null)}
        title="Deactivate package?"
        description={
          deactivatePkg
            ? `"${deactivatePkg.name}" will no longer be offered to healers and clinics.`
            : undefined
        }
        confirmLabel="Deactivate"
        destructive
        loading={deactivate.isPending}
        onConfirm={() => {
          if (deactivatePkg) {
            deactivate.mutate(deactivatePkg.id, {
              onSettled: () => setDeactivatePkg(null),
            });
          }
        }}
      />
    </div>
  );
}
