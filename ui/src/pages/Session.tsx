import { useState, useEffect, useRef } from "react";
import { Skeleton } from "../components/ui/skeleton";
import { Clock, CheckCircle2, X } from "lucide-react";
import { useParams } from "react-router-dom";
import api from "../lib/axios";
import { usePatient } from "../context/PatientContext";

const COMMON_SYMPTOMS = [
  "Headache",
  "Fever",
  "Cough",
  "Fatigue",
  "Nausea",
  "Dizziness",
];

export interface HealingSession {
  id: string;
  patientId: string;
  healerId: string;

  medicalCondition?: string;
  healingDetails?: string;
  labFindings?: string;

  complementaryTreatments?: boolean;
  complementaryDetails?: string;

  infectiousIllness?: boolean;
  infectiousDetails?: string;

  basicBookPg?: string;
  advancedBookPg?: string;
  psychotherapyPg?: string;

  status: CaseStatus;

  createdAt: string; // ISO string (Instant equivalent)
  updatedAt: string;
  medicalSnapshot: MedicalSnapshot;
}

interface MedicalSnapshot {
  generalHealth: string;
  diet: string;
  exercise: string;
  exerciseFrequency: string;
  hypertension: boolean;
  giIssues: boolean;
  stressDepressionAnxiety: boolean;
  pregnant: boolean;
  cancer: boolean;
  cancerLocation: string;
  cancerDetails: string
  additionalNotes: string;
}

interface PageNumbers {
  basicBookPg: string;
  advancedBookPg: string;
  psychotherapyPg: string;
}

type CaseStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";

export default function Session() {
  const [isLoading, setIsLoading] = useState(false);
  const [symptoms, setSymptoms] = useState<string[]>(["Headache", "Fatigue"]);
  const [symptomInput, setSymptomInput] = useState("");
  const [showSuggestions, setShowSuggestions] = useState(false);
  const suggestionsRef = useRef<HTMLDivElement>(null);
  const { healingCaseId } = useParams();
  const [healingSession, setHealingSession] = useState<any>(null);
  const { selectedPatient } = usePatient();
  const [conditions, setConditions] = useState<string>("");
  const [sessionCount, setSessionCount] = useState<number>(0);
  const [lastVisit, setLastVisit] = useState<string>("");
  const [medicalCondition, setMedicalCondition] = useState<string>("");
  const [protocols, setProtocols] = useState<string[]>(["Hypertension Protocol"]);
  const [protocolInput, setProtocolInput] = useState("");
  const [filteredProtocols, setFilteredProtocols] = useState<string[]>([]);
  const [pageNumbers, setPageNumbers] = useState<PageNumbers>(null);
  const [complementaryModalities, setComplementaryModalities] = useState<string>("");
  const [infectiousIllness, setInfectiousIllness] = useState<string>("");
  const [labFindings, setLabFindings] = useState<string>("");
  const [medications, setMedications] = useState<string[]>([]);
  const [medicationInput, setMedicationInput] = useState<string>("");
  const [filteredMedications, setFilteredMedications] = useState<string[]>([]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (suggestionsRef.current && !suggestionsRef.current.contains(event.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  async function fetchHealingSession() {
    try {
      setIsLoading(true);
      const response = await api.get(`/api/v1/cases/${healingCaseId}`);
      setHealingSession(response.data);
      setConditions(() => {
        const snapshot = response.data.medicalSnapshot;
        const list: string[] = [];
        if (snapshot.hypertension) list.push("Hypertension");
        if (snapshot.giIssues) list.push("GI Issues");
        if (snapshot.stressDepressionAnxiety) list.push("Stress, Depression, Anxiety");
        if (snapshot.cancer) list.push("Cancer");
        return list.join(" | ");
      });
      const healingSession = response.data?.healingSession;
      setSessionCount(healingSession?.length + 1);
      setLastVisit(healingSession?.length > 0 ? formatLastVisit(healingSession[healingSession?.length - 1].createdAt) : "");
    } catch (error) {
      console.error("Error fetching healing session:", error);
    } finally {
      setIsLoading(false);
    }
  }

  const formatLastVisit = (date: string) => {
    const now = new Date().getTime();
    const lastVisitedDate = new Date(date).getTime();
    const diff = now - lastVisitedDate;
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days < 7) {
      return `${days} day${days !== 1 ? "s" : ""}`;
    }
    const weeks = Math.floor(days / 7);
    if (weeks < 4) {
      return `${weeks} week${weeks !== 1 ? "s" : ""}`;
    }
    const months = Math.floor(weeks / 4);
    if (months < 12) {
      return `${months} month${months !== 1 ? "s" : ""}`;
    }
    const years = Math.floor(months / 12);
    return `${years} year${years !== 1 ? "s" : ""}`;
  }

  const hasFetched = useRef(false);

  useEffect(() => {
    if (hasFetched.current) return;
    hasFetched.current = true;

    fetchHealingSession();
  }, [healingCaseId]);

  const filteredSymptoms = COMMON_SYMPTOMS.filter(
    (s) =>
      s.toLowerCase().includes(symptomInput.toLowerCase()) &&
      !symptoms.includes(s)
  );

  const handleAddSymptom = (symptom: string) => {
    if (symptom.trim() && !symptoms.includes(symptom.trim())) {
      setSymptoms([...symptoms, symptom.trim()]);
    }
    setSymptomInput("");
    setShowSuggestions(false);
  };

  const handleRemoveSymptom = (symptomToRemove: string) => {
    setSymptoms(symptoms.filter((s) => s !== symptomToRemove));
  };

  const handleAddProtocol = (protocol: string) => {
    if (protocol.trim() && !protocols.includes(protocol.trim())) {
      setProtocols([...protocols, protocol.trim()]);
    }
    setProtocolInput("");
    setShowSuggestions(false);
  };

  const handlePageNumberChange = (book: string, pageNumber: string) => {
    setPageNumbers((prev) => ({
      ...prev,
      [book]: pageNumber,
    }));
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-12 w-3/4" />
        <Skeleton className="h-20 w-full" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  function handleRemoveProtocol(protocolToRemove: string): void {
    setProtocols(protocols.filter((p) => p !== protocolToRemove));
  }

  function handleRemoveMedication(medication: string): void {
    setMedications(medications.filter((m) => m !== medication));
  }

  function handleAddMedication(medication: string): void {
    if (medication.trim() && !medications.includes(medication.trim())) {
      setMedications([...medications, medication.trim()]);
    }
    setMedicationInput("");
    setShowSuggestions(false);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-primary text-white p-4 rounded-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h1 className="text-xl font-bold flex items-center gap-2">
          <span className="text-2xl">🟣</span> Healing Session — {selectedPatient?.firstName + " " + selectedPatient?.lastName}
        </h1>
        <div className="flex items-center gap-3 text-sm">
          {/* <span className="bg-white/20 px-3 py-1.5 rounded-lg flex items-center gap-2">
            <Clock className="w-4 h-4" /> 00:24:30
          </span> */}
          <span className="bg-success px-3 py-1.5 rounded-lg flex items-center gap-2 font-medium">
            {healingSession?.status}
          </span>
        </div>
      </div>

      {/* Patient Summary */}
      <div className="bg-primary/5 p-4 rounded-xl flex flex-wrap gap-x-8 gap-y-2 text-sm border border-primary/10">
        <span><strong>Age:</strong> {selectedPatient?.age} | <strong>Gender:</strong>{" " + selectedPatient?.gender}</span>
        <span><strong>Conditions:</strong>{` ${conditions}`}</span>
        <span><strong>Session:</strong> {sessionCount}</span>
        {lastVisit && <span><strong>Last Visit:</strong> {lastVisit}</span>}
      </div>

      <div className="bg-card border border-border rounded-xl p-6 shadow-sm space-y-6">
        <div className="relative">
          <label className="block text-sm font-semibold text-text-light mb-2">
            Medical Condition / Diagnosis
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            <textarea
              placeholder="Please state the main ailment that will be treated and highlight important point in it..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={medicalCondition}
              rows={4}
              onChange={(e) => {
                setMedicalCondition(e.target.value);
              }}
            />
          </div>
        </div>

        {/* Symptoms */}
        <div className="relative" ref={suggestionsRef}>
          <label className="block text-sm font-semibold text-text-light mb-2">
            Chief Complaint / Symptoms
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            {symptoms.map((symptom) => (
              <span
                key={symptom}
                className="bg-primary/10 text-primary px-3 py-1 rounded-md text-sm font-medium flex items-center gap-2"
              >
                {symptom}
                <X
                  className="w-3 h-3 cursor-pointer hover:text-primary-dark"
                  onClick={() => handleRemoveSymptom(symptom)}
                />
              </span>
            ))}
            <input
              type="text"
              placeholder="Type to add more symptoms..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={symptomInput}
              onChange={(e) => {
                setSymptomInput(e.target.value);
                setShowSuggestions(true);
              }}
              onFocus={() => setShowSuggestions(true)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && symptomInput.trim()) {
                  e.preventDefault();
                  handleAddSymptom(symptomInput);
                }
              }}
            />
          </div>
          {showSuggestions && filteredSymptoms.length > 0 && (
            <div className="absolute z-10 w-full mt-1 bg-white border border-border rounded-lg shadow-lg max-h-48 overflow-y-auto">
              {filteredSymptoms.map((symptom) => (
                <div
                  key={symptom}
                  className="px-4 py-2 text-sm cursor-pointer hover:bg-primary/5 hover:text-primary transition-colors"
                  onClick={() => handleAddSymptom(symptom)}
                >
                  {symptom}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Ailment Row */}
        {/* <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-semibold text-text-light mb-2">Ailment Category</label>
            <select className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white">
              <option>Cardiovascular</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-semibold text-text-light mb-2">Specific Ailment</label>
            <select className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white">
              <option>Hypertension</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-semibold text-text-light mb-2">Severity</label>
            <select className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white">
              <option>Moderate</option>
            </select>
          </div>
        </div> */}

        {/* Protocols */}
        <div className="relative" ref={suggestionsRef}>
          <label className="block text-sm font-semibold text-text-light mb-2">
            Protocol(s) Used
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            {protocols.map((protocol) => (
              <span
                key={protocol}
                className="bg-primary/10 text-primary px-3 py-1 rounded-md text-sm font-medium flex items-center gap-2"
              >
                {protocol}
                <X
                  className="w-3 h-3 cursor-pointer hover:text-primary-dark"
                  onClick={() => handleRemoveProtocol(protocol)}
                />
              </span>
            ))}
            <input
              type="text"
              placeholder="Type to add more protocols..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={protocolInput}
              onChange={(e) => {
                setProtocolInput(e.target.value);
                setShowSuggestions(true);
              }}
              onFocus={() => setShowSuggestions(true)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && protocolInput.trim()) {
                  e.preventDefault();
                  handleAddProtocol(protocolInput);
                }
              }}
            />
          </div>
          {showSuggestions && filteredProtocols.length > 0 && (
            <div className="absolute z-10 w-full mt-1 bg-white border border-border rounded-lg shadow-lg max-h-48 overflow-y-auto">
              {filteredProtocols.map((protocol) => (
                <div
                  key={protocol}
                  className="px-4 py-2 text-sm cursor-pointer hover:bg-primary/5 hover:text-primary transition-colors"
                  onClick={() => handleAddProtocol(protocol)}
                >
                  {protocol}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="relative" ref={suggestionsRef}>
          <label className="block text-sm font-semibold text-text-light mb-2">
            Medications
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            {medications.map((medication) => (
              <span
                key={medication}
                className="bg-primary/10 text-primary px-3 py-1 rounded-md text-sm font-medium flex items-center gap-2"
              >
                {medication}
                <X
                  className="w-3 h-3 cursor-pointer hover:text-primary-dark"
                  onClick={() => handleRemoveMedication(medication)}
                />
              </span>
            ))}
            <input
              type="text"
              placeholder="Type to add more symptoms..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={medicationInput}
              onChange={(e) => {
                setMedicationInput(e.target.value);
                setShowSuggestions(true);
              }}
              onFocus={() => setShowSuggestions(true)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && medicationInput.trim()) {
                  e.preventDefault();
                  handleAddMedication(medicationInput);
                }
              }}
            />
          </div>
          {showSuggestions && filteredMedications.length > 0 && (
            <div className="absolute z-10 w-full mt-1 bg-white border border-border rounded-lg shadow-lg max-h-48 overflow-y-auto">
              {filteredMedications.map((medication) => (
                <div
                  key={medication}
                  className="px-4 py-2 text-sm cursor-pointer hover:bg-primary/5 hover:text-primary transition-colors"
                  onClick={() => handleAddMedication(medication)}
                >
                  {medication}
                </div>
              ))}
            </div>
          )}
        </div>

        <div>
          <label className="block text-sm font-semibold text-text-light mb-2 uppercase tracking-wider">
            Book page numbers
          </label>
          <div className="bg-background rounded-xl p-4 flex flex-wrap justify-around">
            <div>
              <label className="block text-sm font-semibold text-text-light mb-2 uppercase tracking-wider">
                Basic Book pg no
              </label>
              <input type="number" className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white" onChange={(e) => handlePageNumberChange("basic", e.target.value)} />
            </div>
            <div>
              <label className="block text-sm font-semibold text-text-light mb-2 uppercase tracking-wider">
                Advanced Book pg no
              </label>
              <input type="number" className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white" onChange={(e) => handlePageNumberChange("advanced", e.target.value)} />
            </div>
            <div>
              <label className="block text-sm font-semibold text-text-light mb-2 uppercase tracking-wider">
                Psychotherapy Book pg no
              </label>
              <input type="number" className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white" onChange={(e) => handlePageNumberChange("psychotherapy", e.target.value)} />
            </div>
          </div>
        </div>

        <div className="relative">
          <label className="block text-sm font-semibold text-text-light mb-2">
            Lab Findings
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            <textarea
              placeholder="Please mention lab findings if any..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={medicalCondition}
              rows={4}
              onChange={(e) => {
                setLabFindings(e.target.value);
              }}
            />
          </div>
        </div>

        <div className="relative">
          <label className="block text-sm font-semibold text-text-light mb-2">
            Complementary Modalities
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            <textarea
              placeholder="Please mention complementary modalities if any..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={medicalCondition}
              rows={4}
              onChange={(e) => {
                setComplementaryModalities(e.target.value);
              }}
            />
          </div>
        </div>

        <div className="relative">
          <label className="block text-sm font-semibold text-text-light mb-2">
            Infectious Illness
          </label>
          <div className="border-2 border-border rounded-lg p-2 flex flex-wrap gap-2 min-h-[48px] items-center focus-within:border-primary transition-colors bg-white">
            <textarea
              placeholder="Please mention infectious illness if any..."
              className="flex-1 min-w-[200px] outline-none text-sm bg-transparent"
              value={medicalCondition}
              rows={4}
              onChange={(e) => {
                setInfectiousIllness(e.target.value);
              }}
            />
          </div>
        </div>

        {/* Chakra Scan (Simplified Grid) */}
        <div>
          <label className="block text-sm font-semibold text-text-light mb-2 uppercase tracking-wider">
            PRE-SCAN — Chakra Energy Levels
          </label>
          <div className="bg-background rounded-xl p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
            {[
              { name: "Crown", color: "bg-chakra-crown", state: "Normal" },
              { name: "Ajna", color: "bg-chakra-ajna", state: "Depleted" },
              { name: "Throat", color: "bg-chakra-throat", state: "Normal" },
              { name: "Heart (F)", color: "bg-chakra-heart", state: "Sl. Depleted", highlight: "border-warning bg-warning/10" },
              { name: "Solar Plexus", color: "bg-chakra-solar", state: "Congested", highlight: "border-danger bg-danger/10" },
              { name: "Navel", color: "bg-chakra-navel", state: "Normal" },
              { name: "Basic", color: "bg-chakra-basic", state: "Depleted" },
            ].map((chakra, idx) => (
              <div key={idx} className={`flex items-center gap-2 p-2 bg-white rounded-lg border ${chakra.highlight || 'border-border'}`}>
                <div className={`w-3.5 h-3.5 rounded-full flex-shrink-0 ${chakra.color}`} />
                <span className="text-sm flex-1 truncate">{chakra.name}</span>
                <select className="text-xs p-1 border border-border rounded bg-transparent outline-none w-24">
                  <option>{chakra.state}</option>
                </select>
              </div>
            ))}
            <button className="text-sm text-primary font-medium p-2 hover:bg-primary/5 rounded-lg border border-transparent transition-colors text-left">
              + Add More Chakras
            </button>
          </div>
        </div>

        {/* Treatment Steps */}
        <div>
          <label className="block text-sm font-semibold text-text-light mb-2">Treatment Steps</label>
          <div className="bg-background rounded-xl p-3 space-y-2">
            {[
              { num: 1, tag: "General Sweep", tagColor: "bg-success/10 text-success", desc: "Whole Aura — LWG" },
              { num: 2, tag: "Localized Sweep", tagColor: "bg-warning/10 text-warning", desc: "Solar Plexus (F & B) — LWG, 30 cycles" },
              { num: 3, tag: "Energize", tagColor: "bg-blue-100 text-blue-700", desc: "Heart (F) — LWG + LWV" },
              { num: 4, tag: "Stabilize", tagColor: "bg-primary/10 text-primary", desc: "Heart (F) — LWB" },
            ].map((step) => (
              <div key={step.num} className="flex items-center gap-3 p-3 bg-white rounded-lg border border-border">
                <span className="font-bold text-primary w-5">{step.num}.</span>
                <span className={`px-2 py-1 rounded text-xs font-semibold ${step.tagColor}`}>
                  {step.tag}
                </span>
                <span className="text-sm text-text flex-1">{step.desc}</span>
                <span className="text-text-light cursor-grab">☰</span>
              </div>
            ))}
            <button className="w-full py-3 mt-2 border-2 border-dashed border-border rounded-lg text-sm font-medium text-text-light hover:border-primary hover:text-primary transition-colors">
              + Add Treatment Step
            </button>
          </div>
        </div>

        {/* Notes */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-semibold text-text-light mb-2">Healer Notes (Private)</label>
            <textarea rows={3} className="w-full p-3 border-2 border-border rounded-lg text-sm focus:border-primary outline-none" placeholder="Session observations..."></textarea>
          </div>
          <div>
            <label className="block text-sm font-semibold text-text-light mb-2">Patient Feedback</label>
            <textarea rows={3} className="w-full p-3 border-2 border-border rounded-lg text-sm focus:border-primary outline-none" placeholder="How does the patient feel?"></textarea>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="flex flex-col sm:flex-row justify-between items-center gap-4 pt-4 border-t border-border">
          <button className="w-full sm:w-auto px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors">
            💾 Save as Draft
          </button>
          <div className="flex w-full sm:w-auto gap-3">
            <button className="flex-1 sm:flex-none px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors">
              Post-Scan ➡️
            </button>
            <button className="flex-1 sm:flex-none px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors flex items-center justify-center gap-2">
              <CheckCircle2 className="w-5 h-5" /> Complete Session
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
