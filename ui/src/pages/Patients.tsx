import { useState } from "react";
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { Skeleton } from "../components/ui/skeleton";
import {
  Search,
  Plus,
  Link as URLIcon,
  CircleCheck,
  Eye,
  Play,
  BookText,
  ClipboardList,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import api from "../lib/axios";
import { ComputedPatient, Patient, usePatient } from "../context/PatientContext";
import { ClosurePopup } from "./Closure";
import { useQueryClient } from "@tanstack/react-query";
import { UpgradeModal } from "../components/UpgradeModal";
import { useAuth } from "../context/AuthContext";

const columnHelper = createColumnHelper<ComputedPatient>();

export default function Patients() {
  const [openPopup, setOpenPopup] = useState<boolean>(false);
  const [generatedLink, setGeneratedLink] = useState<string>("");
  const { patients, isLoading, selectPatient } = usePatient();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [closurePatient, setClosurePatient] = useState<Patient | null>(null);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [pendingHealingCaseId, setPendingHealingCaseId] = useState<string | null>(null);

  const generatePdf = async (healingCaseId: string) => {
    try {
      await api.post("/api/v1/cases/generate-pdf", null, {
        params: { healingCaseId },
      });
    } catch (err: any) {
      if (err?.response?.status === 402) {
        setPendingHealingCaseId(healingCaseId);
        setShowUpgradeModal(true);
      } else {
        console.error("PDF generation failed", err);
      }
    }
  };

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
      cell: (info) => (
        <span
          className={`px-2 py-1 rounded-md text-xs font-semibold ${
            info.getValue() === "ACTIVE"
              ? "bg-success/10 text-success"
              : "bg-text-light/10 text-text-light"
          }`}
        >
          {info.getValue()}
        </span>
      ),
    }),
    columnHelper.accessor("id", {
      header: "Actions",
      cell: (info) => {
        const patient = info.row.original;

        const isDraft = patient.status === "DRAFT";
        const isActive = patient.status === "ACTIVE";
        const isCompleted = patient.status === "CLOSED";

        const handleClosureButtonClick = (patient: Patient) => {
          if (isActive) setClosurePatient(patient);
          if (isCompleted) generatePdf(patient.healingCaseId);
        };

        return (
          <div className="flex items-center gap-2">
            <Link
              to={`/session/${patient.healingCaseId}`}
              onClick={() => handleSelectPatient(patient)}
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary
               px-3 py-1.5 text-white text-xs font-medium hover:bg-primary-dark transition-colors"
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
              onClick={() => handleClosureButtonClick(patient)}
              className={`inline-flex items-center gap-1.5 rounded-lg
                ${
                  isActive || isCompleted
                    ? "bg-primary hover:bg-primary-dark cursor-pointer"
                    : "bg-border pointer-events-none"
                }
                px-3 py-1.5 text-white text-xs font-medium transition-colors`}
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
                className="inline-flex items-center gap-1.5 rounded-lg border border-primary text-primary px-3 py-1.5 text-xs font-medium hover:bg-primary/5 transition-colors"
              >
                <ClipboardList className="w-3.5 h-3.5" />
                Case Summary
              </button>
            )}
          </div>
        );
      },
    }),
  ];

  const handleSelectPatient = (patient: Patient) => {
    selectPatient(patient.id);
  };

  const table = useReactTable({
    data: patients,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  const handleGenerateLink = async () => {
    setOpenPopup(true);
    const res = await api.get("/api/v1/patients/generate-link");
    if (res.status === 200) {
      setGeneratedLink(res.data.link);
    } else {
      console.error("Failed to generate registration link");
    }
  };

  return (
    <div className="space-y-6">
      {openPopup && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-lg p-6 w-full max-w-md space-y-4">
            <h2 className="text-lg font-semibold text-primary-dark">
              Self Registration Link
            </h2>
            <p className="text-sm text-text-light">
              Share this link with the patient. It will expire in 30 minutes.
            </p>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={generatedLink}
                readOnly
                className="flex-1 border border-border rounded-lg px-3 py-2 text-sm"
              />
              <button
                onClick={() => {
                  navigator.clipboard.writeText(generatedLink);
                }}
                className="bg-primary text-white px-3 py-2 rounded-lg text-sm"
              >
                Copy
              </button>
            </div>
            <div className="flex justify-end">
              <button
                onClick={() => setOpenPopup(false)}
                className="text-sm px-4 py-2 rounded-lg border border-border hover:bg-gray-100"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {closurePatient && (
        <ClosurePopup
          patient={closurePatient}
          onClose={() => setClosurePatient(null)}
          onSuccess={() => {
            setClosurePatient(null);
            queryClient.invalidateQueries({ queryKey: ['patients'] });
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
          <button
            onClick={handleGenerateLink}
            className="bg-primary hover:bg-primary-dark text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2 transition-colors hover:cursor-pointer"
          >
            <URLIcon className="w-4 h-4" />
            Generate Self Registration Link
          </button>
          <Link
            to="/registration"
            className="bg-primary hover:bg-primary-dark text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2 transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Patient
          </Link>
        </div>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
        <div className="p-4 border-b border-border flex items-center gap-3">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-light" />
            <input
              type="text"
              placeholder="Search patients..."
              className="w-full pl-9 pr-4 py-2 border border-border rounded-lg text-sm focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
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
            generatePdf(id);
          }}
        />
      )}
    </div>
  );
}
