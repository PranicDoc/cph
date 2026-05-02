import { useMemo, useState } from "react";
import { usePatient } from "../context/PatientContext";
import { HealingSession } from "../interfaces";

export function SessionHeader(props: { healingSession: HealingSession }) {
  const { selectedPatient } = usePatient();
  const { healingSession } = props;

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
  };

  const conditions = useMemo(() => {
    const snapshot = healingSession?.medicalSnapshot;
    if (!snapshot) return "";
    const list = [];
    if (snapshot.hypertension) list.push("Hypertension");
    if (snapshot.giIssues) list.push("GI Issues");
    if (snapshot.stressDepressionAnxiety)
      list.push("Stress, Depression, Anxiety");
    if (snapshot.cancer) list.push("Cancer");
    return list.join(" | ");
  }, [healingSession?.medicalSnapshot]);

  const sessionCount = useMemo(() => {
    return (healingSession?.healingSession?.length ?? 0) + 1;
  }, [healingSession?.healingSession]);

  const lastVisit = useMemo(() => {
    const sessions = healingSession?.healingSession;
    if (!sessions?.length) return "";
    return formatLastVisit(sessions[sessions.length - 1].createdAt);
  }, [healingSession?.healingSession]);

  return (
    <>
      {/* Header */}
      <div className="bg-primary text-white p-4 rounded-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h1 className="text-xl font-bold flex items-center gap-2">
          <span className="text-2xl">🟣</span> Healing Session —{" "}
          {selectedPatient?.firstName + " " + selectedPatient?.lastName}
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
        <span>
          <strong>Age:</strong> {selectedPatient?.age} |{" "}
          <strong>Gender:</strong>
          {" " + selectedPatient?.gender}
        </span>
        <span>
          <strong>Conditions:</strong>
          {` ${conditions}`}
        </span>
        <span>
          <strong>Session:</strong> {sessionCount}
        </span>
        {lastVisit && (
          <span>
            <strong>Last Visit:</strong> {lastVisit}
          </span>
        )}
      </div>
    </>
  );
}
