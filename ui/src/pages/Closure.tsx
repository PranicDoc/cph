import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
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
  const closeMutation = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${patient.healingCaseId}/close`, {
        comments,
        isHealingContinued,
      }),
    onSuccess: () => { toast.success("Case closed"); onSuccess(); },
    onError: (e) => toast.error(extractDetail(e) || "Failed to close case", { duration: 5000 }),
  });

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
            disabled={closeMutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-lg border border-border hover:bg-gray-50 transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            onClick={() => closeMutation.mutate()}
            disabled={closeMutation.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-dark transition-colors disabled:opacity-50 flex items-center gap-2"
          >
            {closeMutation.isPending ? "Closing..." : "Close Case"}
          </button>
        </div>
      </div>
    </div>
  );
}
