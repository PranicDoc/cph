import { useForm } from "@tanstack/react-form";
import { sessionFormSchema } from "./sessionFormSchema";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import api from "../lib/axios";
import { useEffect, useRef, useState } from "react";
import { Skeleton } from "../components/ui/skeleton";
import { useParams } from "react-router-dom";
import { usePatient } from "../context/PatientContext";

export default function SessionForm({ patientForm }: any) {
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const { healingCaseId } = useParams();
  const { selectedPatient } = usePatient();
  const suggestionsRef = useRef<HTMLDivElement>(null);
  const [healingSession, setHealingSession] = useState<any>(null);
  const [sessionCount, setSessionCount] = useState<number>(0);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [conditions, setConditions] = useState<string>("");
  const [lastVisit, setLastVisit] = useState<string>("");

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

  const form = useForm({
    defaultValues: {
      medicalCondition: "",
      healingDetails: "",
      sympotoms: [],
      protocols: [],
      medications: [],
      basicBookPg: "",
      advancedBookPg: "",
      psychotherapyBookPg: "",
      complementaryDetails: "",
      infectiousDetails: "",
      labFindings: "",
    },

    onSubmit: async ({ value }) => {
      const payload = {
        healingCaseId: healingCaseId,
        ...value,
        complementaryTreatments: !!value?.complementaryDetails,
        infectiousIllness: !!value?.infectiousDetails
      };

      try {
        await api.patch(`/api/v1/cases/${healingCaseId}`, payload);
      } catch (e) {
        console.error(e);
      }
    },
  });

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
      <form
        onSubmit={(e) => {
          e.preventDefault();
          form.handleSubmit();
        }}
        className="space-y-6"
      >
        <DynamicFormEngine form={form} schema={sessionFormSchema} />

        <button
          type="submit"
          className="px-6 py-2 bg-primary text-white rounded"
        >
          Submit
        </button>
      </form>
    </div>
  );
}