import { useState } from "react";
import { X } from "lucide-react";
import api from "../lib/axios";
import { Patient } from "../context/PatientContext";

type ClosurePopupProps = {
  patient: Patient;
  onClose: () => void;
  onSuccess: () => void;
};

export function ClosurePopup({
  patient,
  onClose,
  onSuccess,
}: ClosurePopupProps) {
  const [comments, setComments] = useState("");
  const [isHealingContinued, setIsHealingContinued] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleCloseCase = async () => {
    setIsSubmitting(true);
    try {
      const res = await api.post(`/api/v1/cases/${patient.healingCaseId}/close`, {
        comments,
        isHealingContinued,
      });

      if (res.status === 200 || res.status === 201) {
        onSuccess();
      }
    } catch (error) {
      console.error("Failed to close case:", error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-lg p-6 w-full max-w-md space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-primary-dark">
            Close Case: {patient.firstName} {patient.lastName}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors "
            title="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="space-y-4 py-2">
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-gray-700">
              Additional Comments
            </label>
            <textarea
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary min-h-[100px] resize-none"
              placeholder="Add any final notes or observations here..."
            />
          </div>

          <label className="flex items-center gap-2 cursor-pointer w-fit">
            <input
              type="checkbox"
              checked={isHealingContinued}
              onChange={(e) => setIsHealingContinued(e.target.checked)}
              className="w-4 h-4 rounded border-gray-300 text-primary focus:ring-primary cursor-pointer"
            />
            <span className="text-sm font-medium text-gray-700">
              Is healing continued?
            </span>
          </label>
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="px-4 py-2 text-sm font-medium rounded-lg border border-border hover:bg-gray-50 transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            onClick={handleCloseCase}
            disabled={isSubmitting}
            className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-dark transition-colors disabled:opacity-50 flex items-center gap-2"
          >
            {isSubmitting ? "Closing..." : "Close Case"}
          </button>
        </div>
      </div>
    </div>
  );
}
