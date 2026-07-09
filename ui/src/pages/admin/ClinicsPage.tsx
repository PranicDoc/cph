import { Fragment, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Plus, Pencil, ChevronDown, ChevronRight, Users } from "lucide-react";
import {
  useAdminClinics, useCreateClinic, useUpdateClinic, useToggleClinicActive,
  type ClinicItem,
} from "../../hooks/admin/useAdminClinics";
import { useClinicHealersByClinicId, useToggleHealerActive } from "../../hooks/admin/useAdminHealers";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TextField } from "@/components/form/Field";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

const fieldCls = "border px-3 py-2.5";

/* ── Toggle-active button (clinics + healers) ── */
function ToggleActiveButton({
  active,
  disabled,
  onClick,
  bordered = false,
}: {
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
  bordered?: boolean;
}) {
  return (
    <Button
      variant={bordered ? "outline" : "ghost"}
      size="xs"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "h-auto px-2.5 py-1 text-xs font-medium",
        active
          ? cn("text-red-600 hover:bg-red-50 hover:text-red-600", bordered && "border-red-200")
          : cn("text-green-600 hover:bg-green-50 hover:text-green-600", bordered && "border-green-200")
      )}
    >
      {active ? "Deactivate" : "Activate"}
    </Button>
  );
}

/* ── Create modal ── */
function CreateClinicModal({ onClose }: { onClose: () => void }) {
  const createMutation = useCreateClinic();

  const form = useForm({
    defaultValues: { code: "", name: "", phone: "", email: "" },
    onSubmit: async ({ value }) => {
      createMutation.mutate(
        {
          code: value.code,
          name: value.name,
          phone: value.phone || undefined,
          email: value.email || undefined,
        },
        { onSuccess: onClose }
      );
    },
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 gap-0 max-h-[90vh] overflow-y-auto max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        <DialogTitle className="text-lg font-bold text-text mb-4">New Clinic</DialogTitle>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
          className="space-y-3"
        >
          <div>
            <Label className="block text-xs text-text-light mb-1">
              Clinic Code <span className="text-red-400">*</span>
            </Label>
            <form.Field
              name="code"
              children={(field) => (
                <TextField field={field} placeholder="e.g. CHNY01 — max 20 chars" className={fieldCls} />
              )}
            />
          </div>
          <div>
            <Label className="block text-xs text-text-light mb-1">
              Clinic Name <span className="text-red-400">*</span>
            </Label>
            <form.Field
              name="name"
              children={(field) => <TextField field={field} className={fieldCls} />}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">Phone</Label>
              <form.Field
                name="phone"
                children={(field) => <TextField field={field} type="tel" className={fieldCls} />}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">Email</Label>
              <form.Field
                name="email"
                children={(field) => <TextField field={field} type="email" className={fieldCls} />}
              />
            </div>
          </div>
          <div className="flex gap-2 pt-1">
            <Button type="submit" disabled={createMutation.isPending} className="flex-1 py-2.5 h-auto">
              {createMutation.isPending ? "Creating…" : "Create Clinic"}
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

/* ── Edit modal ── */
function EditClinicModal({ clinic, onClose }: { clinic: ClinicItem; onClose: () => void }) {
  const updateMutation = useUpdateClinic(clinic.id);

  const form = useForm({
    defaultValues: {
      name: clinic.name,
      phone: clinic.phone ?? "",
      email: clinic.email ?? "",
      city: clinic.city ?? "",
      state: clinic.state ?? "",
    },
    onSubmit: async ({ value }) => {
      updateMutation.mutate(
        {
          code: clinic.code,
          name: value.name,
          phone: value.phone || undefined,
          email: value.email || undefined,
          city: value.city || undefined,
          state: value.state || undefined,
        },
        { onSuccess: onClose }
      );
    },
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 gap-0 max-h-[90vh] overflow-y-auto max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
        <DialogTitle className="text-lg font-bold text-text mb-4">Edit Clinic</DialogTitle>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
          className="space-y-3"
        >
          <div>
            <Label className="block text-xs text-text-light mb-1">Clinic Code</Label>
            <Input
              type="text"
              className={cn(fieldCls, "bg-background text-text-light cursor-not-allowed")}
              value={clinic.code}
              readOnly
              disabled
            />
          </div>
          <div>
            <Label className="block text-xs text-text-light mb-1">
              Clinic Name <span className="text-red-400">*</span>
            </Label>
            <form.Field
              name="name"
              children={(field) => <TextField field={field} className={fieldCls} />}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">Phone</Label>
              <form.Field
                name="phone"
                children={(field) => <TextField field={field} type="tel" className={fieldCls} />}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">Email</Label>
              <form.Field
                name="email"
                children={(field) => <TextField field={field} type="email" className={fieldCls} />}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs text-text-light mb-1">City</Label>
              <form.Field
                name="city"
                children={(field) => <TextField field={field} className={fieldCls} />}
              />
            </div>
            <div>
              <Label className="block text-xs text-text-light mb-1">State</Label>
              <form.Field
                name="state"
                children={(field) => <TextField field={field} className={fieldCls} />}
              />
            </div>
          </div>
          <div className="flex gap-2 pt-1">
            <Button type="submit" disabled={updateMutation.isPending} className="flex-1 py-2.5 h-auto">
              {updateMutation.isPending ? "Saving…" : "Save Changes"}
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

/* ── Healer sub-row (SUPADM expanded view) ── */
function ClinicHealerRows({ clinicId }: { clinicId: string }) {
  const { data: healers = [], isLoading } = useClinicHealersByClinicId(clinicId);
  const toggleActive = useToggleHealerActive();

  if (isLoading) {
    return (
      <TableRow>
        <TableCell colSpan={6} className="px-8 py-3 bg-background text-xs text-text-light">Loading healers…</TableCell>
      </TableRow>
    );
  }
  if (healers.length === 0) {
    return (
      <TableRow>
        <TableCell colSpan={6} className="px-8 py-3 bg-background text-xs text-text-light">No healers in this clinic.</TableCell>
      </TableRow>
    );
  }
  return (
    <>
      {healers.map((h) => (
        <TableRow key={h.id} className="bg-background/70 border-t border-border/50">
          <TableCell className="px-8 py-2.5 text-xs text-text-light whitespace-normal" colSpan={2}>
            <div className="font-medium text-text">{h.fullName}</div>
            <div className="text-text-light">{h.email}</div>
          </TableCell>
          <TableCell className="px-4 py-2.5 text-xs text-text-light">{h.role}</TableCell>
          <TableCell className="px-4 py-2.5">
            <StatusBadge active={h.active} />
          </TableCell>
          <TableCell className="px-4 py-2.5 text-xs text-text-light">{new Date(h.createdAt).toLocaleDateString()}</TableCell>
          <TableCell className="px-4 py-2.5">
            <ToggleActiveButton
              active={h.active}
              disabled={toggleActive.isPending}
              onClick={() => toggleActive.mutate({ id: h.id, active: !h.active })}
            />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

/* ── Main page ── */
export default function ClinicsPage() {
  const { data: clinics = [], isLoading, isError } = useAdminClinics();
  const toggleClinicActive = useToggleClinicActive();

  const [showCreate, setShowCreate] = useState(false);
  const [editingClinic, setEditingClinic] = useState<ClinicItem | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const toggleExpand = (id: string) => setExpandedId((prev) => (prev === id ? null : id));

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-4 gap-3">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-text">Clinics</h1>
          <p className="text-sm text-text-light mt-0.5">Manage clinic accounts.</p>
        </div>
        <Button onClick={() => setShowCreate(true)} className="px-3 py-2 md:px-4 h-auto shrink-0">
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">New Clinic</span>
          <span className="sm:hidden">New</span>
        </Button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load clinics.</p>}

      {!isLoading && !isError && (
        <>
          {/* Desktop table — manual rows because of the expandable healer sub-rows */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden bg-card">
            <Table>
              <TableHeader>
                <TableRow className="bg-background hover:bg-background border-b border-border">
                  {["Code", "Name", "City", "Status", "Created", "Actions"].map((h) => (
                    <TableHead
                      key={h}
                      className="h-auto px-4 py-3 text-xs font-medium text-text-light uppercase tracking-wide"
                    >
                      {h}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody className="divide-y divide-border">
                {clinics.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="px-4 py-8 text-center text-text-light">
                      No clinics yet. Create one to get started.
                    </TableCell>
                  </TableRow>
                ) : (
                  clinics.map((c) => (
                    <Fragment key={c.id}>
                      <TableRow
                        className="hover:bg-background/60 transition-colors cursor-pointer border-0"
                        onClick={() => toggleExpand(c.id)}
                      >
                        <TableCell className="px-4 py-3 font-mono text-xs text-text-light">{c.code}</TableCell>
                        <TableCell className="px-4 py-3 font-medium text-text">
                          <span className="flex items-center gap-1.5">
                            {expandedId === c.id
                              ? <ChevronDown className="w-3.5 h-3.5 text-text-light shrink-0" />
                              : <ChevronRight className="w-3.5 h-3.5 text-text-light shrink-0" />}
                            {c.name}
                          </span>
                        </TableCell>
                        <TableCell className="px-4 py-3 text-xs text-text-light">{c.city || "—"}</TableCell>
                        <TableCell className="px-4 py-3">
                          <StatusBadge active={c.active} />
                        </TableCell>
                        <TableCell className="px-4 py-3 text-xs text-text-light">
                          {new Date(c.createdAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                          <div className="flex items-center gap-1.5">
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => setEditingClinic(c)}
                              className="h-auto w-auto p-1.5 hover:bg-primary/10 text-text-light hover:text-primary"
                              title="Edit"
                            >
                              <Pencil className="w-3.5 h-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => toggleExpand(c.id)}
                              className="h-auto w-auto p-1.5 hover:bg-primary/10 text-text-light hover:text-primary"
                              title="Healers"
                            >
                              <Users className="w-3.5 h-3.5" />
                            </Button>
                            <ToggleActiveButton
                              active={c.active}
                              disabled={toggleClinicActive.isPending}
                              onClick={() => toggleClinicActive.mutate({ id: c.id, active: !c.active })}
                            />
                          </div>
                        </TableCell>
                      </TableRow>
                      {expandedId === c.id && <ClinicHealerRows clinicId={c.id} />}
                    </Fragment>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          {/* Mobile card list */}
          <div className="md:hidden space-y-3">
            {clinics.length === 0 ? (
              <p className="text-center text-text-light text-sm py-8">
                No clinics yet. Create one to get started.
              </p>
            ) : (
              clinics.map((c) => (
                <div key={c.id} className="border border-border rounded-xl p-4">
                  <div className="flex items-start justify-between mb-1">
                    <div>
                      <span className="font-semibold text-text">{c.name}</span>
                      <span className="font-mono text-xs text-text-light block mt-0.5">{c.code}</span>
                    </div>
                    <StatusBadge active={c.active} />
                  </div>
                  <div className="flex items-center gap-2 mt-3 pt-3 border-t border-border">
                    <Button
                      variant="outline"
                      size="xs"
                      onClick={() => setEditingClinic(c)}
                      className="h-auto px-3 py-1.5 gap-1 text-xs font-normal text-text"
                    >
                      <Pencil className="w-3 h-3" /> Edit
                    </Button>
                    <Button
                      variant="outline"
                      size="xs"
                      onClick={() => toggleExpand(c.id)}
                      className="h-auto px-3 py-1.5 gap-1 text-xs font-normal text-text"
                    >
                      <Users className="w-3 h-3" /> Healers
                    </Button>
                    <ToggleActiveButton
                      active={c.active}
                      bordered
                      disabled={toggleClinicActive.isPending}
                      onClick={() => toggleClinicActive.mutate({ id: c.id, active: !c.active })}
                    />
                  </div>
                  {expandedId === c.id && <MobileHealerList clinicId={c.id} />}
                </div>
              ))
            )}
          </div>
        </>
      )}

      {showCreate && <CreateClinicModal onClose={() => setShowCreate(false)} />}
      {editingClinic && <EditClinicModal clinic={editingClinic} onClose={() => setEditingClinic(null)} />}
    </div>
  );
}

/* ── Mobile healer list ── */
function MobileHealerList({ clinicId }: { clinicId: string }) {
  const { data: healers = [], isLoading } = useClinicHealersByClinicId(clinicId);
  const toggleActive = useToggleHealerActive();

  if (isLoading) return <p className="text-xs text-text-light mt-3">Loading healers…</p>;
  if (healers.length === 0) return <p className="text-xs text-text-light mt-3">No healers in this clinic.</p>;

  return (
    <div className="mt-3 pt-3 border-t border-border space-y-2">
      {healers.map((h) => (
        <div key={h.id} className="flex items-center justify-between py-1">
          <div>
            <p className="text-xs font-medium text-text">{h.fullName}</p>
            <p className="text-xs text-text-light">{h.role}</p>
          </div>
          <ToggleActiveButton
            active={h.active}
            disabled={toggleActive.isPending}
            onClick={() => toggleActive.mutate({ id: h.id, active: !h.active })}
          />
        </div>
      ))}
    </div>
  );
}
