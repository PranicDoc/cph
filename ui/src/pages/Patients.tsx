import { useState } from "react";
import { createColumnHelper } from "@tanstack/react-table";
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
      cell: (info) => {
        const status = info.getValue();
        if (!status) return <span className="px-2 py-1 rounded-md text-xs font-semibold bg-text-light/10 text-text-light">—</span>;
        return (
          <span
            className={`px-2 py-1 rounded-md text-xs font-semibold ${
              status === "ACTIVE"
                ? "bg-success/10 text-success"
                : "bg-text-light/10 text-text-light"
            }`}
          >
            {status}
          </span>
        );
      },
    }),
    columnHelper.accessor("id", {
      header: "Actions",
      cell: (info) => {
        const patient = info.row.original;
        if (!patient.healingCaseId) return <span className="text-xs text-text-light">No case</span>;

        const isDraft = patient.status === "DRAFT";
        const isActive = patient.status === "ACTIVE";
        const isCompleted = patient.status === "CLOSED";

        const handleClosureButtonClick = (patient: Patient) => {
          if (isActive) setClosurePatient(patient);
          if (isCompleted) generatePdfMutation.mutate(patient.healingCaseId);
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
              onClick={() => !generatePdfMutation.isPending && handleClosureButtonClick(patient)}
              className={`inline-flex items-center gap-1.5 rounded-lg
                ${
                  isActive || isCompleted
                    ? generatePdfMutation.isPending
                      ? "bg-border pointer-events-none"
                      : "bg-primary hover:bg-primary-dark cursor-pointer"
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

        <DataTable<ComputedPatient>
          columns={columns}
          data={patients}
          isLoading={isLoading}
          loadingRowCount={5}
          enableSorting={false}
          emptyMessage="No patients found."
          className="rounded-none border-0"
          headerRowClassName="bg-primary hover:bg-primary"
          headCellClassName="px-6 py-4 font-semibold text-white text-sm normal-case tracking-normal"
          cellClassName="px-6 py-4"
          rowClassName="hover:bg-primary/5"
        />
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
