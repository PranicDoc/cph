import { useState } from "react";
import { useDeletedPatients, useRestorePatient, type DeletedPatient } from "../../hooks/admin/useDeletedPatients";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

const day = (iso: string) => new Date(iso).toLocaleDateString();

/** Super admin: patients their healers deleted. They can be restored until the retention window ends. */
export function DeletedPatientsPanel() {
  const { data, isLoading, isError } = useDeletedPatients();
  const restore = useRestorePatient();
  const [target, setTarget] = useState<DeletedPatient | null>(null);

  return (
    <section className="mt-10" aria-labelledby="deleted-patients-title">
      <h2 id="deleted-patients-title" className="text-lg font-semibold text-text">Deleted patients</h2>
      <p className="text-sm text-text-light mt-1 mb-3">
        Restore a patient (with their cases) until the date shown; after that they are erased for good.
      </p>
      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Could not load the deleted patients.</p>}
      {data && data.length === 0 && <p className="text-text-light">No deleted patients.</p>}
      {data && data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-text-light">
                <th className="py-2 pr-4">Code</th>
                <th className="py-2 pr-4">Patient</th>
                <th className="py-2 pr-4">Healer</th>
                <th className="py-2 pr-4">Cases</th>
                <th className="py-2 pr-4">Deleted</th>
                <th className="py-2 pr-4">Erased on</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {data.map((p) => (
                <tr key={p.id} className="border-t border-border">
                  <td className="py-2 pr-4 font-mono">{p.patientCode}</td>
                  <td className="py-2 pr-4">{p.firstName} {p.lastName}</td>
                  <td className="py-2 pr-4">{p.healerName ?? "—"}</td>
                  <td className="py-2 pr-4">{p.caseCount}</td>
                  <td className="py-2 pr-4">{day(p.deletedAt)}</td>
                  <td className="py-2 pr-4">{day(p.purgeAt)}</td>
                  <td className="py-2 text-right">
                    <Button size="sm" variant="outline" onClick={() => setTarget(p)}>Restore</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ConfirmDialog
        open={!!target}
        onOpenChange={(open) => !open && setTarget(null)}
        title="Restore this patient?"
        description={
          target && (
            <span>
              {target.firstName} {target.lastName} ({target.patientCode}) and {target.caseCount} case
              {target.caseCount === 1 ? "" : "s"} will reappear in {target.healerName ?? "their healer"}&apos;s list.
            </span>
          )
        }
        confirmLabel="Restore"
        loading={restore.isPending}
        onConfirm={() => target && restore.mutate(target.id, { onSettled: () => setTarget(null) })}
      />
    </section>
  );
}
