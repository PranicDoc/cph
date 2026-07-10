import { useMemo, useState } from "react";
import { createColumnHelper, flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Search,
  Plus,
  Link as URLIcon,
  CircleCheck,
  Eye,
  Play,
  BookText,
  ClipboardList,
  Pen,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import api from "../lib/axios";
import {
  ComputedPatient,
  Patient,
  usePatient,
} from "../context/PatientContext";
import { ClosurePopup } from "./Closure";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UpgradeModal } from "../components/UpgradeModal";
import { useAuth } from "../context/AuthContext";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
import { Skeleton } from "@/components/ui/skeleton";

const columnHelper = createColumnHelper<ComputedPatient>();

export default function Patients() {
  const [openPopup, setOpenPopup] = useState<boolean>(false);
  const [generatedLink, setGeneratedLink] = useState<string>("");
  const [search, setSearch] = useState<string>("");
  const { patients, isLoading, selectPatient } = usePatient();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [closurePatient, setClosurePatient] = useState<Patient | null>(null);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [pendingHealingCaseId, setPendingHealingCaseId] = useState<
    string | null
  >(null);
  const generatePdfMutation = useMutation({
    mutationKey: ["generate-pdf"],
    mutationFn: (healingCaseId: string) =>
      api.post("/api/v1/cases/generate-pdf", null, {
        params: { healingCaseId },
      }),
    onSuccess: () => toast.success("PDF queued successfully"),
    onError: (err: any, healingCaseId: string) => {
      if (err?.response?.status === 402) {
        setPendingHealingCaseId(healingCaseId);
        setShowUpgradeModal(true);
      } else {
        toast.error(extractDetail(err) || "Failed to generate PDF", {
          duration: 5000,
        });
      }
    },
  });

  const columns = [
    columnHelper.accessor("firstName", {
      header: "First Name",
      cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    }),
    columnHelper.accessor("lastName", {
      header: "Last Name",
    }),
    columnHelper.accessor("dob", {
      header: "Date of Birth",
    }),
    columnHelper.accessor("gender", {
      header: "Gender",
    }),
    columnHelper.accessor("contact", {
      header: "Phone",
    }),
    columnHelper.accessor("status", {
      header: "Status",
      cell: (info) => renderStatusPill(info.getValue()),
    }),
    columnHelper.accessor("id", {
      header: "Actions",
      cell: (info) => renderActions(info.row.original, "table"),
    }),
  ];

  const handleSelectPatient = (patient: Patient) => {
    selectPatient(patient.id);
  };

  const renderStatusPill = (status?: string) => (
    <span
      className={`px-2 py-1 rounded-md text-xs font-semibold ${
        status === "ACTIVE"
          ? "bg-success/10 text-success"
          : "bg-text-light/10 text-text-light"
      }`}
    >
      {status || "—"}
    </span>
  );

  const renderActions = (
    patient: ComputedPatient,
    variant: "table" | "card"
  ) => {
    if (!patient.healingCaseId)
      return <span className="text-xs text-text-light">No case</span>;

    const isDraft = patient.status === "DRAFT";
    const isActive = patient.status === "ACTIVE";
    const isCompleted = patient.status === "CLOSED";
    const isCard = variant === "card";
    const buttonSize = isCard
      ? "flex-1 justify-center min-w-[100px] px-3 py-2"
      : "px-3 py-1.5";

    const handleClosureButtonClick = (patient: Patient) => {
      if (isActive) setClosurePatient(patient);
      if (isCompleted) generatePdfMutation.mutate(patient.healingCaseId);
    };

    return (
      <div
        className={
          isCard ? "flex flex-wrap gap-2" : "flex items-center gap-2"
        }
      >
        <Link
          to={`/session/${patient.healingCaseId}`}
          onClick={() => handleSelectPatient(patient)}
          className={`inline-flex items-center gap-1.5 rounded-lg bg-primary
           ${buttonSize} text-white text-xs font-medium hover:bg-primary-dark transition-colors`}
        >
          {isDraft ? (
            <>
              <Play className="w-3.5 h-3.5 fill-white" />
              Start
            </>
          ) : (
            <>
              <Eye className="w-3.5 h-3.5" />
              View
            </>
          )}
        </Link>
        <div
          onClick={() => !generatePdfMutation.isPending && handleClosureButtonClick(patient)}
          className={`inline-flex items-center gap-1.5 rounded-lg
            ${
              isActive || isCompleted
                ? generatePdfMutation.isPending
                  ? "bg-border pointer-events-none"
                  : "bg-primary hover:bg-primary-dark cursor-pointer"
                : "bg-border pointer-events-none"
            }
            ${buttonSize} text-white text-xs font-medium transition-colors`}
        >
          {isCompleted ? (
            <>
              <BookText className="w-3.5 h-3.5" />
              Generate PDF
            </>
          ) : (
            <>
              <CircleCheck className="w-3.5 h-3.5" />
              Complete
            </>
          )}
        </div>
        {isCompleted && (
          <button
            onClick={() => navigate(`/cases/${patient.healingCaseId}/summary`)}
            className={`inline-flex items-center gap-1.5 rounded-lg border border-primary text-primary ${buttonSize} text-xs font-medium hover:bg-primary/5 transition-colors`}
          >
            <ClipboardList className="w-3.5 h-3.5" />
            Case Summary
          </button>
        )}
      </div>
    );
  };

  const filteredPatients = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return patients;
    return patients.filter((patient) =>
      [patient.firstName, patient.lastName, patient.fullName, patient.contact]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(query))
    );
  }, [patients, search]);

  const table = useReactTable({
    data: filteredPatients,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  const handleGenerateLink = async () => {
    setOpenPopup(true);
    try {
      const res = await api.get("/api/v1/patients/generate-link");
      setGeneratedLink(res.data.link);
    } catch (err) {
      setOpenPopup(false);
      toast.error(extractDetail(err) || "Failed to generate registration link", { duration: 5000 });
    }
  };

  return (
    <div className="space-y-6">
      <Dialog open={openPopup} onOpenChange={setOpenPopup}>
        <DialogContent className="w-full max-w-md space-y-4 gap-0">
          <DialogTitle className="text-lg font-semibold text-primary-dark">
            Self Registration Link
          </DialogTitle>
          <p className="text-sm text-text-light">
            Share this link with the patient. It will expire in 30 minutes.
          </p>
          <div className="flex items-center gap-2">
            <Input
              type="text"
              value={generatedLink}
              readOnly
              className="flex-1 border px-3 py-2"
            />
            <Button
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(generatedLink);
                  toast.success("Link copied to clipboard");
                } catch {
                  toast.error("Copy failed — please copy the link manually", { duration: 5000 });
                }
              }}
              className="px-3 font-normal"
            >
              Copy
            </Button>
          </div>
          <div className="flex justify-end">
            <Button
              variant="outline"
              onClick={() => setOpenPopup(false)}
              className="font-normal hover:bg-gray-100"
            >
              Close
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {closurePatient && (
        <ClosurePopup
          patient={closurePatient}
          onClose={() => setClosurePatient(null)}
          onSuccess={() => {
            setClosurePatient(null);
            queryClient.invalidateQueries({ queryKey: ["patients"] });
          }}
        />
      )}

      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-primary-dark">
            Patients Directory
          </h1>
          <p className="text-text-light text-sm">
            Manage and view all registered patients.
          </p>
        </div>
        <div className="flex gap-x-4">
          <Button
            onClick={handleGenerateLink}
            className="px-4 py-2 h-auto gap-2 text-base font-medium hover:cursor-pointer"
          >
            <URLIcon className="w-4 h-4" />
            <span className="hidden sm:inline">
              Generate Self Registration Link
            </span>
            <span className="sm:hidden">Self-Reg Link</span>
          </Button>
          <Link
            to="/registration"
            className="bg-primary hover:bg-primary-dark text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2 transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">New Patient</span>
            <span className="sm:hidden">New</span>
          </Link>
        </div>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
        <div className="p-4 border-b border-border flex items-center gap-3">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-light" />
            <Input
              type="text"
              placeholder="Search patients..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-4 py-2 border border-border rounded-lg text-sm focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
            />
          </div>
        </div>

        <div className="hidden md:block overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-primary text-white">
              {table.getHeaderGroups().map((headerGroup) => (
                <tr key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <th key={header.id} className="px-6 py-4 font-semibold">
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext()
                          )}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <tr key={idx} className="border-b border-border">
                    {columns.map((_, colIdx) => (
                      <td key={colIdx} className="px-6 py-4">
                        <Skeleton className="h-5 w-full max-w-[120px]" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : table.getRowModel().rows.length > 0 ? (
                table.getRowModel().rows.map((row) => (
                  <tr
                    key={row.id}
                    className="border-b border-border hover:bg-primary/5 transition-colors"
                  >
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="px-6 py-4 text-text">
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext()
                        )}
                      </td>
                    ))}
                  </tr>
                ))
              ) : (
                <tr>
                  <td
                    colSpan={columns.length}
                    className="px-6 py-8 text-center text-text-light"
                  >
                    No patients found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="md:hidden p-4 space-y-3">
          {isLoading ? (
            Array.from({ length: 3 }).map((_, idx) => (
              <div
                key={idx}
                className="border border-border rounded-xl p-4 space-y-3"
              >
                <Skeleton className="h-5 w-32" />
                <Skeleton className="h-4 w-48" />
                <Skeleton className="h-8 w-full" />
              </div>
            ))
          ) : filteredPatients.length > 0 ? (
            filteredPatients.map((patient) => (
              <div
                key={patient.id}
                className="border border-border rounded-xl p-4"
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="font-semibold text-text">
                    {patient.fullName}
                  </span>
                  {renderStatusPill(patient.status)}
                </div>
                <p className="text-xs text-text-light mt-1">
                  {[
                    patient.dob && `DOB ${patient.dob}`,
                    patient.gender,
                    patient.contact,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
                <div className="border-t border-border pt-3 mt-3">
                  {renderActions(patient, "card")}
                </div>
              </div>
            ))
          ) : (
            <p className="py-8 text-center text-text-light">
              No patients found.
            </p>
          )}
        </div>
      </div>

      {showUpgradeModal && pendingHealingCaseId && (
        <UpgradeModal
          isOpen={showUpgradeModal}
          healingCaseId={pendingHealingCaseId}
          ownerId={user?.id ?? ""}
          ownerType="HEALER"
          onClose={() => {
            setShowUpgradeModal(false);
            setPendingHealingCaseId(null);
          }}
          onRetry={(id) => {
            setShowUpgradeModal(false);
            setPendingHealingCaseId(null);
            generatePdfMutation.mutate(id);
          }}
        />
      )}
    </div>
  );
}
