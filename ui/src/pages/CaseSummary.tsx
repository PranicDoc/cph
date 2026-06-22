import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, BookText, User, Stethoscope, Pill, Activity, CheckCircle2, XCircle, Download } from "lucide-react";
import api from "../lib/axios";
import { useState } from "react";
import { Skeleton } from "../components/ui/skeleton";

// ---- Types ----

interface ChakraReading {
  id: string;
  chakraName: string;
  energyLevel: string;
  activationLevel: string;
  notes?: string;
}

interface ChakraScan {
  id: string;
  scanType: "PRE" | "POST";
  notes?: string;
  readings: ChakraReading[];
}

interface SymptomFeedback {
  id: string;
  caseSymptomId: string;
  rating: number;
  notes?: string;
}

interface QualitativeFeedback {
  id: string;
  feedbackType: "INTERIM" | "FINAL";
  feedbackText?: string;
  patientRemarks?: string;
}

interface HealingSession {
  id: string;
  sessionNumber: number;
  sessionDate: string;
  startTime?: string;
  endTime?: string;
  status: string;
  scans: ChakraScan[];
  symptomFeedbacks: SymptomFeedback[];
  qualitativeFeedbacks: QualitativeFeedback[];
}

interface CaseSymptom {
  id: string;
  symptomName: string;
  description?: string;
  severity?: number;
  addedDate?: string;
  resolvedDate?: string;
  resolved: boolean;
}

interface CaseMedication {
  id: string;
  medicationName: string;
  dosage?: string;
  morningDose?: boolean;
  afternoonDose?: boolean;
  nightDose?: boolean;
  notes?: string;
}

interface CaseClosure {
  closureDate?: string;
  additionalComments?: string;
  healingCount?: number;
  isHealingContinued?: boolean;
  lastFeedbackDate?: string;
}

interface CaseMedicalSnapshot {
  generalHealth?: string;
  diet?: string;
  exercise?: string;
  exerciseFrequency?: string;
  hypertension?: boolean;
  giIssues?: boolean;
  stressDepressionAnxiety?: boolean;
  pregnant?: boolean;
  cancer?: boolean;
  cancerLocation?: string;
  cancerDetails?: string;
  additionalNotes?: string;
}

interface CaseSummaryData {
  patientFirstName: string;
  patientLastName: string;
  dob?: string;
  gender?: string;
  maritalStatus?: string;
  contact?: string;
  occupation?: string;
  caseId: string;
  medicalCondition?: string;
  labFindings?: string[];
  complementaryTreatments?: boolean;
  complementaryDetails?: string;
  infectiousIllness?: boolean;
  infectiousDetails?: string;
  basicBookPg?: string;
  advancedBookPg?: string;
  psychotherapyPg?: string;
  caseCreatedAt: string;
  medicalSnapshot?: CaseMedicalSnapshot;
  symptoms: CaseSymptom[];
  medications: CaseMedication[];
  sessions: HealingSession[];
  closure?: CaseClosure;
}

// ---- Helpers ----

const fmt = (val: string | undefined | null) => val || "—";

const yesNo = (val: boolean | undefined | null) =>
  val === true ? "Yes" : val === false ? "No" : "—";

const formatDate = (val: string | undefined | null) => {
  if (!val) return "—";
  try {
    return new Date(val).toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  } catch {
    return val;
  }
};

const formatTime = (val: string | undefined | null) => {
  if (!val) return "—";
  return val.substring(0, 5);
};

// ---- Sub-components ----

function SectionHeading({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <div className="flex items-center gap-2 mb-3">
      <span className="text-primary">{icon}</span>
      <h2 className="text-base font-semibold text-primary-dark">{label}</h2>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-start gap-0.5 sm:gap-4 py-1.5 border-b border-border last:border-0">
      <span className="text-xs font-medium text-text-light w-40 shrink-0">{label}</span>
      <span className="text-sm text-text">{value}</span>
    </div>
  );
}

function Badge({ label, variant = "default" }: { label: string; variant?: "default" | "success" | "muted" }) {
  const cls =
    variant === "success"
      ? "bg-success/10 text-success"
      : variant === "muted"
      ? "bg-text-light/10 text-text-light"
      : "bg-primary/10 text-primary";
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${cls}`}>
      {label}
    </span>
  );
}

function ChakraScanTable({ scan }: { scan: ChakraScan }) {
  return (
    <div className="mb-3">
      <p className="text-xs font-semibold text-text-light uppercase tracking-wide mb-1">
        {scan.scanType} Scan
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-xs border border-border rounded-lg overflow-hidden">
          <thead className="bg-primary/5">
            <tr>
              <th className="px-3 py-2 text-left font-medium text-text-light">Chakra</th>
              <th className="px-3 py-2 text-left font-medium text-text-light">Energy</th>
              <th className="px-3 py-2 text-left font-medium text-text-light">Activation</th>
            </tr>
          </thead>
          <tbody>
            {scan.readings.map((r) => (
              <tr key={r.id} className="border-t border-border">
                <td className="px-3 py-1.5 font-medium text-text">{r.chakraName}</td>
                <td className="px-3 py-1.5 text-text-light">{fmt(r.energyLevel)}</td>
                <td className="px-3 py-1.5 text-text-light">{fmt(r.activationLevel)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function SessionCard({
  session,
  symptoms,
}: {
  session: HealingSession;
  symptoms: CaseSymptom[];
}) {
  const pre = session.scans.find((s) => s.scanType === "PRE");
  const post = session.scans.find((s) => s.scanType === "POST");

  return (
    <div className="border border-border rounded-xl p-4 space-y-4 bg-card">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="w-8 h-8 rounded-full bg-primary text-white text-sm font-bold flex items-center justify-center">
            {session.sessionNumber}
          </span>
          <div>
            <p className="font-medium text-text-dark text-sm">{formatDate(session.sessionDate)}</p>
            {(session.startTime || session.endTime) && (
              <p className="text-xs text-text-light">
                {formatTime(session.startTime)} – {formatTime(session.endTime)}
              </p>
            )}
          </div>
        </div>
        <Badge
          label={session.status}
          variant={session.status === "COMPLETED" ? "success" : "muted"}
        />
      </div>

      {/* Chakra Scans */}
      {(pre || post) && (
        <div className="grid md:grid-cols-2 gap-4">
          {pre && <ChakraScanTable scan={pre} />}
          {post && <ChakraScanTable scan={post} />}
        </div>
      )}

      {/* Symptom Ratings */}
      {session.symptomFeedbacks.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-text-light uppercase tracking-wide mb-2">
            Symptom Ratings
          </p>
          <div className="flex flex-wrap gap-2">
            {session.symptomFeedbacks.map((sf) => {
              const symptom = symptoms.find((s) => s.id === sf.caseSymptomId);
              return (
                <div
                  key={sf.id}
                  className="flex items-center gap-1.5 border border-border rounded-lg px-2.5 py-1 text-xs"
                >
                  <span className="text-text-light">{symptom?.symptomName ?? "Symptom"}</span>
                  <span className="font-semibold text-primary">{sf.rating}/10</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Qualitative Feedback */}
      {session.qualitativeFeedbacks.length > 0 && (
        <div className="space-y-2">
          <p className="text-xs font-semibold text-text-light uppercase tracking-wide">
            Qualitative Feedback
          </p>
          {session.qualitativeFeedbacks.map((qf) => (
            <div key={qf.id} className="bg-primary/5 rounded-lg p-3 space-y-1">
              <Badge label={qf.feedbackType} />
              {qf.feedbackText && (
                <p className="text-sm text-text mt-1">{qf.feedbackText}</p>
              )}
              {qf.patientRemarks && (
                <p className="text-xs text-text-light italic">
                  Patient: {qf.patientRemarks}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function LabFindingsList({ keys }: { keys: string[] }) {
  const [loading, setLoading] = useState<string | null>(null);

  if (!keys.length) return <span className="text-sm text-text-light">—</span>;

  const download = async (key: string) => {
    setLoading(key);
    try {
      const { data } = await api.get("/api/v1/cases/download", { params: { fileUrl: key } });
      window.open(data.data.url, "_blank", "noopener,noreferrer");
    } finally {
      setLoading(null);
    }
  };

  const fileName = (key: string) => key.split("/").pop() ?? key;

  return (
    <ul className="space-y-1.5 mt-1">
      {keys.map((key) => (
        <li key={key}>
          <button
            type="button"
            onClick={() => download(key)}
            disabled={loading === key}
            className="flex items-center gap-2 text-sm text-primary hover:underline disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5 flex-shrink-0" />
            <span className="truncate max-w-xs">{fileName(key)}</span>
            {loading === key && <span className="text-xs text-text-light">Opening...</span>}
          </button>
        </li>
      ))}
    </ul>
  );
}

// ---- Main Page ----

export default function CaseSummary() {
  const { healingCaseId } = useParams<{ healingCaseId: string }>();
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery<CaseSummaryData>({
    queryKey: ["case-summary", healingCaseId],
    queryFn: async () => {
      const res = await api.get(`/api/v1/cases/${healingCaseId}/summary`);
      return res.data.data;
    },
    enabled: !!healingCaseId,
  });

  if (isLoading) {
    return (
      <div className="space-y-6 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-text-light gap-3">
        <XCircle className="w-10 h-10 text-error" />
        <p className="text-sm">Failed to load case summary.</p>
        <button
          onClick={() => navigate(-1)}
          className="text-primary text-sm underline underline-offset-2"
        >
          Go back
        </button>
      </div>
    );
  }

  const patientName = `${data.patientFirstName} ${data.patientLastName}`;

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 text-sm text-text-light hover:text-primary transition-colors mb-2"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to Patients
          </button>
          <h1 className="text-2xl font-bold text-primary-dark">Case Summary</h1>
          <p className="text-sm text-text-light">
            {patientName} · Opened {formatDate(data.caseCreatedAt)}
            {data.closure?.closureDate && ` · Closed ${formatDate(data.closure.closureDate)}`}
          </p>
        </div>

        {/* TODO: wire up once pdfWorker supports DocumentType.SUMMARY_DOC */}
        <div className="relative group">
          <button
            disabled
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-border text-text-light text-sm font-medium cursor-not-allowed"
          >
            <BookText className="w-4 h-4" />
            Generate PDF
          </button>
          <div className="absolute right-0 top-full mt-1 hidden group-hover:block bg-text-dark text-white text-xs rounded px-2 py-1 whitespace-nowrap z-10">
            Coming soon
          </div>
        </div>
      </div>

      {/* Patient & Case Info */}
      <div className="grid md:grid-cols-2 gap-4">
        <div className="bg-card border border-border rounded-xl p-4">
          <SectionHeading icon={<User className="w-4 h-4" />} label="Patient Information" />
          <InfoRow label="Full Name" value={patientName} />
          <InfoRow label="Date of Birth" value={formatDate(data.dob)} />
          <InfoRow label="Gender" value={fmt(data.gender)} />
          <InfoRow label="Marital Status" value={fmt(data.maritalStatus)} />
          <InfoRow label="Contact" value={fmt(data.contact)} />
          <InfoRow label="Occupation" value={fmt(data.occupation)} />
        </div>

        <div className="bg-card border border-border rounded-xl p-4">
          <SectionHeading icon={<Stethoscope className="w-4 h-4" />} label="Case Details" />
          <InfoRow label="Medical Condition" value={fmt(data.medicalCondition)} />
          {data.labFindings && data.labFindings.length > 0 && (
            <div className="flex flex-col sm:flex-row sm:items-start gap-0.5 sm:gap-4 py-1.5 border-b border-border">
              <span className="text-xs font-medium text-text-light w-40 shrink-0">Lab Findings</span>
              <LabFindingsList keys={data.labFindings} />
            </div>
          )}
          <InfoRow label="Complementary Treatments" value={yesNo(data.complementaryTreatments)} />
          {data.complementaryTreatments && (
            <InfoRow label="Complementary Details" value={fmt(data.complementaryDetails)} />
          )}
          <InfoRow label="Infectious Illness" value={yesNo(data.infectiousIllness)} />
          {data.infectiousIllness && (
            <InfoRow label="Infectious Details" value={fmt(data.infectiousDetails)} />
          )}
          <InfoRow label="Basic Book Pg" value={fmt(data.basicBookPg)} />
          <InfoRow label="Advanced Book Pg" value={fmt(data.advancedBookPg)} />
          {data.psychotherapyPg && (
            <InfoRow label="Psychotherapy Pg" value={data.psychotherapyPg} />
          )}
        </div>
      </div>

      {/* Medical Snapshot */}
      {data.medicalSnapshot && (
        <div className="bg-card border border-border rounded-xl p-4">
          <SectionHeading icon={<Activity className="w-4 h-4" />} label="Medical History (at case start)" />
          <div className="grid sm:grid-cols-2 gap-x-8">
            <InfoRow label="General Health" value={fmt(data.medicalSnapshot.generalHealth)} />
            <InfoRow label="Diet" value={fmt(data.medicalSnapshot.diet)} />
            <InfoRow label="Exercise" value={yesNo(data.medicalSnapshot.exercise === "YES" ? true : data.medicalSnapshot.exercise === "NO" ? false : undefined)} />
            {data.medicalSnapshot.exercise === "YES" && (
              <InfoRow label="Exercise Frequency" value={fmt(data.medicalSnapshot.exerciseFrequency)} />
            )}
            <InfoRow label="Hypertension" value={yesNo(data.medicalSnapshot.hypertension)} />
            <InfoRow label="GI Issues" value={yesNo(data.medicalSnapshot.giIssues)} />
            <InfoRow label="Stress / Depression / Anxiety" value={yesNo(data.medicalSnapshot.stressDepressionAnxiety)} />
            <InfoRow label="Pregnant" value={yesNo(data.medicalSnapshot.pregnant)} />
            <InfoRow label="Cancer" value={yesNo(data.medicalSnapshot.cancer)} />
            {data.medicalSnapshot.cancer && (
              <>
                <InfoRow label="Cancer Location" value={fmt(data.medicalSnapshot.cancerLocation)} />
                <InfoRow label="Cancer Details" value={fmt(data.medicalSnapshot.cancerDetails)} />
              </>
            )}
            {data.medicalSnapshot.additionalNotes && (
              <div className="sm:col-span-2">
                <InfoRow label="Additional Notes" value={data.medicalSnapshot.additionalNotes} />
              </div>
            )}
          </div>
        </div>
      )}

      {/* Symptoms */}
      {data.symptoms.length > 0 && (
        <div className="bg-card border border-border rounded-xl p-4">
          <SectionHeading icon={<Activity className="w-4 h-4" />} label="Symptoms" />
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-text-light font-medium border-b border-border">
                  <th className="pb-2 text-left">Symptom</th>
                  <th className="pb-2 text-left">Description</th>
                  <th className="pb-2 text-left">Severity</th>
                  <th className="pb-2 text-left">Added</th>
                  <th className="pb-2 text-left">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.symptoms.map((s) => (
                  <tr key={s.id} className="border-b border-border last:border-0">
                    <td className="py-2 pr-4 font-medium text-text">{s.symptomName}</td>
                    <td className="py-2 pr-4 text-text-light">{fmt(s.description)}</td>
                    <td className="py-2 pr-4 text-text-light">{s.severity ?? "—"}</td>
                    <td className="py-2 pr-4 text-text-light">{formatDate(s.addedDate)}</td>
                    <td className="py-2">
                      {s.resolved ? (
                        <span className="flex items-center gap-1 text-success text-xs font-medium">
                          <CheckCircle2 className="w-3.5 h-3.5" /> Resolved
                        </span>
                      ) : (
                        <span className="text-xs text-text-light">Active</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Medications */}
      {data.medications.length > 0 && (
        <div className="bg-card border border-border rounded-xl p-4">
          <SectionHeading icon={<Pill className="w-4 h-4" />} label="Medications" />
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-text-light font-medium border-b border-border">
                  <th className="pb-2 text-left">Medication</th>
                  <th className="pb-2 text-left">Dosage</th>
                  <th className="pb-2 text-left">When to Take</th>
                  <th className="pb-2 text-left">Notes</th>
                </tr>
              </thead>
              <tbody>
                {data.medications.map((m) => {
                  const timing = [
                    m.morningDose && "Morning",
                    m.afternoonDose && "Afternoon",
                    m.nightDose && "Night",
                  ].filter(Boolean).join(", ") || "—";
                  return (
                    <tr key={m.id} className="border-b border-border last:border-0">
                      <td className="py-2 pr-4 font-medium text-text">{m.medicationName}</td>
                      <td className="py-2 pr-4 text-text-light">{fmt(m.dosage)}</td>
                      <td className="py-2 pr-4 text-text-light">{timing}</td>
                      <td className="py-2 text-text-light">{fmt(m.notes)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Sessions */}
      {data.sessions.length > 0 && (
        <div className="space-y-3">
          <SectionHeading icon={<Activity className="w-4 h-4" />} label={`Sessions (${data.sessions.length})`} />
          {data.sessions.map((session) => (
            <SessionCard key={session.id} session={session} symptoms={data.symptoms} />
          ))}
        </div>
      )}

      {/* Closure */}
      {data.closure && (
        <div className="bg-card border border-border rounded-xl p-4">
          <SectionHeading icon={<CheckCircle2 className="w-4 h-4" />} label="Case Closure" />
          <InfoRow label="Closure Date" value={formatDate(data.closure.closureDate)} />
          <InfoRow label="Total Healings" value={data.closure.healingCount?.toString() ?? "—"} />
          <InfoRow label="Last Feedback Date" value={formatDate(data.closure.lastFeedbackDate)} />
          <InfoRow label="Healing Continued" value={yesNo(data.closure.isHealingContinued)} />
          {data.closure.additionalComments && (
            <InfoRow label="Additional Comments" value={data.closure.additionalComments} />
          )}
        </div>
      )}
    </div>
  );
}
