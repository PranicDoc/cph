import { usePatient } from "../context/PatientContext";
import { HealingSession } from "../interfaces";
import { versioned } from "../ui-version";
import { useSessionSummary } from "./useSessionSummary";

function ClassicSessionHeader(props: { healingSession: HealingSession }) {
  const { selectedPatient } = usePatient();
  const { healingSession } = props;

  const { conditions, sessionCount, lastVisit } = useSessionSummary(healingSession);

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

export const SessionHeader = versioned(ClassicSessionHeader, () => import("../ui-new/session/NewSessionHeader"));
