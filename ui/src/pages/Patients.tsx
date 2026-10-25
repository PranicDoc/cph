import { useMemo, useState } from "react";
import { useUiVersion } from "../ui-version";
import type { ColumnDef } from "@tanstack/react-table";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import {
  Search,
  Plus,
  Link as URLIcon,
  CircleCheck,
  Eye,
  Play,
  CreditCard,
  BookText,
  ClipboardList,
  Pen,
  Filter,
  Check,
} from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Link, useNavigate } from "react-router-dom";
import api from "../lib/axios";
import {
  ComputedPatient,
  Patient,
  PatientListFilters,
  PatientSortOption,
  usePatient,
  usePatientList,
} from "../context/PatientContext";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ClosurePopup } from "./Closure";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UpgradeModal } from "../components/UpgradeModal";
import { ConfirmDialog } from "../components/common/ConfirmDialog";
import { Checkbox } from "@/components/ui/checkbox";
import { useCreditAccount } from "../hooks/useCreditAccount";
import { useAuth } from "../context/AuthContext";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
import { Skeleton } from "@/components/ui/skeleton";

// What the backend falls back to when no sort param is sent.
const BACKEND_DEFAULT_SORT: PatientSortOption = "CASE_DATE_DESC";
const SORT_STORAGE_KEY = "patients.defaultSort";

const SORT_OPTIONS: { value: PatientSortOption; label: string }[] = [
  { value: "CASE_DATE_DESC", label: "Latest case first" },
  { value: "CASE_DATE_ASC", label: "Oldest case first" },
  { value: "NAME_ASC", label: "Name A–Z" },
  { value: "NAME_DESC", label: "Name Z–A" },
];

const isSortOption = (v: string | null): v is PatientSortOption =>
  SORT_OPTIONS.some((o) => o.value === v);

const getStoredDefaultSort = (): PatientSortOption => {
  const stored = localStorage.getItem(SORT_STORAGE_KEY);
  return isSortOption(stored) ? stored : BACKEND_DEFAULT_SORT;
};

export default function Patients() {
  const { isNewView } = useUiVersion();
  const [openPopup, setOpenPopup] = useState<boolean>(false);
  const [generatedLink, setGeneratedLink] = useState<string>("");
  const [search, setSearch] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [startDate, setStartDate] = useState<string>("");
  const [endDate, setEndDate] = useState<string>("");
  const [userDefaultSort, setUserDefaultSort] =
    useState<PatientSortOption>(getStoredDefaultSort);
  const [sort, setSort] = useState<PatientSortOption>(userDefaultSort);
  const { selectPatient } = usePatient();
  const [generatingCaseIds, setGeneratingCaseIds] = useState<Set<string>>(
    new Set()
  );

  const listFilters = useMemo<PatientListFilters>(
    () => ({
      ...(statusFilter !== "ALL" && {
        status: statusFilter as PatientListFilters["status"],
      }),
      ...(startDate && { startDate }),
      ...(endDate && { endDate }),
      ...(sort !== BACKEND_DEFAULT_SORT && { sort }),
    }),
    [statusFilter, startDate, endDate, sort]
  );

  const activeFilterCount =
    (statusFilter !== "ALL" ? 1 : 0) +
    (startDate ? 1 : 0) +
    (endDate ? 1 : 0) +
    (sort !== userDefaultSort ? 1 : 0);

  const resetFilters = () => {
    setStatusFilter("ALL");
    setStartDate("");
    setEndDate("");
    setSort(userDefaultSort);
  };

  const makeSortDefault = () => {
    localStorage.setItem(SORT_STORAGE_KEY, sort);
    setUserDefaultSort(sort);
  };

  // Filtering (status, case-date) and ordering (latest case first) happen on the backend.
  const { patients, isLoading } = usePatientList(listFilters);
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [closurePatient, setClosurePatient] = useState<Patient | null>(null);
  // What to repeat once the healer buys credits (a PDF or unlocking a case); null = dialog closed.
  const [upgradeRetry, setUpgradeRetry] = useState<(() => void) | null>(null);
  const openUpgrade = (retry: () => void) => setUpgradeRetry(() => retry);
  const { data: creditAccount } = useCreditAccount();
  const creditsLeft = creditAccount
    ? creditAccount.free_balance + creditAccount.paid_balance
    : null;
  const outOfCredits = creditsLeft !== null && creditsLeft <= 0;

  // A new case is LOCKED: Start spends one credit to unlock it. The confirmation can be turned off.
  const skipUnlockKey = `unlockCase.skipConfirm.${user?.id ?? ""}`;
  const [unlockTarget, setUnlockTarget] = useState<ComputedPatient | null>(null);
  const [dontAskAgain, setDontAskAgain] = useState(false);
  const unlockMutation = useMutation({
    mutationFn: (patient: ComputedPatient) =>
      api.post(`/api/v1/cases/${patient.healingCaseId}/unlock`),
    onSuccess: (_data, patient) => {
      setUnlockTarget(null);
      queryClient.invalidateQueries({ queryKey: ["patients"] });
      queryClient.invalidateQueries({ queryKey: ["credit-account"] });
      handleSelectPatient(patient);
      navigate(`/session/${patient.healingCaseId}`);
    },
    onError: (err: any, patient) => {
      setUnlockTarget(null);
      if (err?.response?.status === 402) {
        queryClient.invalidateQueries({ queryKey: ["credit-account"] });
        openUpgrade(() => unlockMutation.mutate(patient));
      } else {
        toast.error(extractDetail(err) || "Failed to start the case", { duration: 5000 });
      }
    },
  });
  const handleStart = (patient: ComputedPatient) => {
    let skip = false;
    try {
      skip = localStorage.getItem(skipUnlockKey) === "true";
    } catch {
      // storage unavailable: just ask
    }
    if (skip) {
      unlockMutation.mutate(patient);
    } else {
      setDontAskAgain(false);
      setUnlockTarget(patient);
    }
  };
  const confirmUnlock = () => {
    if (!unlockTarget) return;
    if (dontAskAgain) {
      try {
        localStorage.setItem(skipUnlockKey, "true");
      } catch {
        // storage unavailable: the dialog shows again next time
      }
    }
    unlockMutation.mutate(unlockTarget);
  };
  const generatePdfMutation = useMutation({
    mutationKey: ["generate-pdf"],
    mutationFn: (healingCaseId: string) =>
      api.post("/api/v1/cases/generate-pdf", null, {
        params: { healingCaseId },
      }),
    onSuccess: () => toast.success("PDF queued successfully"),
    onError: (err: any, healingCaseId: string) => {
      if (err?.response?.status === 402) {
        openUpgrade(() => generatePdfMutation.mutate(healingCaseId));
      } else {
        toast.error(extractDetail(err) || "Failed to generate PDF", {
          duration: 5000,
        });
      }
    },
  });

  const columns: ColumnDef<ComputedPatient>[] = [
    {
      accessorKey: "firstName",
      header: "First Name",
      cell: ({ row }) => (
        <span className="font-medium">{row.original.firstName}</span>
      ),
    },
    { accessorKey: "lastName", header: "Last Name" },
    { accessorKey: "dob", header: "Date of Birth" },
    { accessorKey: "gender", header: "Gender" },
    { accessorKey: "contact", header: "Phone" },
    {
      accessorKey: "caseDate",
      header: "Latest Case",
      cell: ({ row }) =>
        row.original.caseDate ? (
          new Date(row.original.caseDate).toLocaleDateString()
        ) : (
          <span className="text-text-light">—</span>
        ),
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => renderStatusPill(row.original.status),
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => renderActions(row.original, "table"),
    },
  ];

  const handleSelectPatient = (patient: Patient) => {
    selectPatient(patient.id);
  };

  // Case status -> badge color, using the shared design-system tokens
  // (see @theme in index.css) so every status stays visually distinct:
  // DRAFT = neutral, ACTIVE = success, CLOSED = primary, CONTINUED = warning.
  const STATUS_PILL_STYLES: Record<string, string> = {
    LOCKED: "bg-warning/10 text-warning",
    DRAFT: "bg-muted text-text-light",
    ACTIVE: "bg-success/10 text-success",
    CLOSED: "bg-primary/10 text-primary",
    CONTINUED: "bg-warning/10 text-warning",
  };

  const NEW_PILL: Record<string, string> = { LOCKED: "warn", DRAFT: "off", ACTIVE: "ok", CLOSED: "", CONTINUED: "warn" };
  const renderStatusPill = (status?: string) =>
    isNewView ? (
      <span className={`badge ${NEW_PILL[status ?? ""] ?? "off"}`}>{status || "-"}</span>
    ) : (
    <span
      className={`px-2 py-1 rounded-md text-xs font-semibold ${
        STATUS_PILL_STYLES[status ?? ""] ?? "bg-muted text-text-light"
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

    if (patient.status === "LOCKED") return renderLockedActions(patient, variant);
    const isDraft = patient.status === "DRAFT";
    const isActive = patient.status === "ACTIVE";
    const isCompleted = patient.status === "CLOSED";
    const isHealingContinued = patient.status === "CONTINUED";
    const isCard = variant === "card";
    const buttonSize = isCard
      ? "flex-1 justify-center min-w-[100px] px-3 py-2"
      : "px-3 py-1.5";

    const handleClosureButtonClick = (patient: Patient) => {
      if (isActive) {
        setClosurePatient(patient);
      }

      if (isCompleted || isHealingContinued) {
        setGeneratingCaseIds((prev) => {
          const next = new Set(prev);
          next.add(patient.healingCaseId);
          return next;
        });

        generatePdfMutation.mutate(patient.healingCaseId, {
          onSettled: () => {
            setGeneratingCaseIds((prev) => {
              const next = new Set(prev);
              next.delete(patient.healingCaseId);
              return next;
            });
          },
        });
      }
    };

    const isGenerating = generatingCaseIds.has(patient.healingCaseId);

    return (
      <div
        className={isCard ? "flex flex-wrap gap-2" : "flex items-center gap-2"}
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
          onClick={() =>
            !isGenerating && handleClosureButtonClick(patient)
          }
          className={`inline-flex items-center gap-1.5 rounded-lg
            ${
              isActive || isCompleted || isHealingContinued
                ? isGenerating
                  ? "bg-border pointer-events-none"
                  : "bg-primary hover:bg-primary-dark cursor-pointer"
                : "bg-border pointer-events-none"
            }
            ${buttonSize} text-white text-xs font-medium transition-colors`}
        >
          {isCompleted || isHealingContinued ? (
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

  // A locked case offers one action, full width: Start (unlock for a credit) or, with no credits
  // left, Upgrade.
  const renderLockedActions = (patient: ComputedPatient, variant: "table" | "card") => {
    const size = variant === "card" ? "py-2" : "py-1.5";
    const unlocking = unlockMutation.isPending && unlockMutation.variables?.id === patient.id;
    const buttonClass = `w-full inline-flex items-center justify-center gap-1.5 rounded-lg bg-primary ${size} text-white text-xs font-medium hover:bg-primary-dark transition-colors disabled:opacity-60`;
    return (
      <div className="w-full">
        {outOfCredits ? (
          <button className={buttonClass} onClick={() => openUpgrade(() => handleStart(patient))}>
            <CreditCard className="w-3.5 h-3.5" />
            Upgrade
          </button>
        ) : (
          <button className={buttonClass} onClick={() => handleStart(patient)} disabled={unlocking}>
            <Play className="w-3.5 h-3.5 fill-white" />
            Start
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

  const handleGenerateLink = async () => {
    setOpenPopup(true);
    try {
      const res = await api.get("/api/v1/patients/generate-link");
      setGeneratedLink(res.data.link);
    } catch (err) {
      setOpenPopup(false);
      toast.error(
        extractDetail(err) || "Failed to generate registration link",
        { duration: 5000 }
      );
    }
  };

  return (
    <div className={isNewView ? "hlx" : "space-y-6"}>
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
                  toast.error("Copy failed — please copy the link manually", {
                    duration: 5000,
                  });
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

      <div className={isNewView ? "page-head" : "flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4"}>
        <div>
          <h1 className="text-2xl font-bold text-primary-dark">
            Patients Directory
          </h1>
          <p className={isNewView ? "muted" : "text-text-light text-sm"} style={isNewView ? { marginTop: 6 } : undefined}>
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
          <Popover>
            <PopoverTrigger asChild>
              <div className="ml-auto flex items-center gap-2 font-normal shrink-0 hover:cursor-pointer">
                <Filter className="w-4 h-4" />
                {activeFilterCount > 0 && (
                  <span className="bg-primary text-white text-xs font-semibold rounded-full w-5 h-5 flex items-center justify-center">
                    {activeFilterCount}
                  </span>
                )}
              </div>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-80">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-text-light">
                  Status
                </label>
                <Select value={statusFilter} onValueChange={setStatusFilter}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="All Statuses" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">All Statuses</SelectItem>
                    <SelectItem value="LOCKED">Locked</SelectItem>
                    <SelectItem value="DRAFT">Draft</SelectItem>
                    <SelectItem value="ACTIVE">Active</SelectItem>
                    <SelectItem value="CLOSED">Closed</SelectItem>
                    <SelectItem value="CONTINUED">Continued</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-text-light">
                  Case date
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    type="date"
                    aria-label="Case date from"
                    value={startDate}
                    max={endDate || undefined}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="flex-1 min-w-0"
                  />
                  <span className="text-sm text-text-light">to</span>
                  <Input
                    type="date"
                    aria-label="Case date to"
                    value={endDate}
                    min={startDate || undefined}
                    onChange={(e) => setEndDate(e.target.value)}
                    className="flex-1 min-w-0"
                  />
                </div>
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-medium text-text-light">
                    Sort by
                  </label>
                  {sort === userDefaultSort ? (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-success/10 text-success">
                      <Check className="w-3 h-3" />
                      Default
                    </span>
                  ) : (
                    <button
                      onClick={makeSortDefault}
                      className="px-2 py-0.5 rounded-full text-xs font-medium bg-primary/10 text-primary hover:bg-primary/20 transition-colors cursor-pointer"
                    >
                      Make default
                    </button>
                  )}
                </div>
                <Select
                  value={sort}
                  onValueChange={(v) => setSort(v as PatientSortOption)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SORT_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                        {option.value === userDefaultSort ? " (default)" : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {activeFilterCount > 0 && (
                <Button
                  variant="outline"
                  onClick={resetFilters}
                  className="w-full font-normal"
                >
                  Reset to defaults
                </Button>
              )}
            </PopoverContent>
          </Popover>
        </div>

        <div className="hidden md:block overflow-x-auto">
          <DataTable<ComputedPatient>
            columns={columns}
            data={filteredPatients}
            isLoading={isLoading}
            loadingRowCount={5}
            enableSorting={false}
            emptyMessage="No patients found."
            className="rounded-none border-0"
          />
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
                    patient.caseDate &&
                      `Case ${new Date(patient.caseDate).toLocaleDateString()}`,
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

      {upgradeRetry && (
        <UpgradeModal
          isOpen
          ownerId={user?.id ?? ""}
          ownerType="HEALER"
          onClose={() => setUpgradeRetry(null)}
          onRetry={() => {
            const retry = upgradeRetry;
            setUpgradeRetry(null);
            queryClient.invalidateQueries({ queryKey: ["credit-account"] });
            queryClient.invalidateQueries({ queryKey: ["patients"] });
            retry();
          }}
        />
      )}
      <ConfirmDialog
        open={!!unlockTarget}
        onOpenChange={(open) => !open && setUnlockTarget(null)}
        title="Start this case?"
        description={
          <span className="flex flex-col gap-3">
            <span>
              Starting this case uses 1 credit
              {creditsLeft !== null ? `. You have ${creditsLeft} left.` : "."}
            </span>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox
                checked={dontAskAgain}
                onCheckedChange={(checked) => setDontAskAgain(checked === true)}
              />
              Don&apos;t show this again
            </label>
          </span>
        }
        confirmLabel="Use 1 credit and start"
        loading={unlockMutation.isPending}
        onConfirm={confirmUnlock}
      />
    </div>
  );
}
