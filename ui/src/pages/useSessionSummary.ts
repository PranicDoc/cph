import { useMemo } from "react";
import { HealingSession } from "../interfaces";

/** What the session header shows besides the patient: conditions, session number, time since the last visit. */
export function useSessionSummary(healingSession: HealingSession) {
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

  return { conditions, sessionCount, lastVisit };
}
