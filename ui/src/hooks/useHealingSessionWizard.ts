import { useCallback, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { ChakraDocEntry } from "../components/ChakraScan";
import { useReferenceData } from "./useReferenceData";
import { useHealingSessionForm } from "./useHealingSessionForm";
import { constructHealingSessionPayload, extractDetail } from "../lib/utils";
import api from "../lib/axios";
import { toast } from "sonner";

const FINAL_FORM_STEP = 2;
const COMPLETED_STEP = 3;

interface UseHealingSessionWizardParams {
  caseId: string;
  symptoms: any[];
  /** Set when correcting a saved session: saving updates it instead of creating a new one. */
  sessionId?: string;
  /** The saved session's date when correcting one; otherwise the session date starts at today. */
  initialSessionDate?: string;
  /** The saved session's number when correcting one. */
  initialSessionNumber?: number | null;
  /** Pre-filled (editable) number for a new session: the healer still confirms or changes it. */
  suggestedSessionNumber?: number;
  onSaved?: () => void;
}

function mapScanEntry(chakra: ChakraDocEntry) {
  return {
    chakraId: chakra.id,
    chakraName: chakra.name,
    energyLevel: chakra.energyLevel,
    activationLevel: chakra.activationLevel,
    notes: chakra.notes ?? "",
  };
}

export function useHealingSessionWizard({
  caseId,
  symptoms,
  sessionId,
  initialSessionDate,
  initialSessionNumber,
  suggestedSessionNumber,
  onSaved,
}: UseHealingSessionWizardParams) {
  const [step, setStep] = useState(1);
  const [preScanChakraData, setPreScanChakraData] = useState<ChakraDocEntry[]>(
    []
  );
  const [postScanChakraData, setPostScanChakraData] = useState<
    ChakraDocEntry[]
  >([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // One Idempotency-Key per session submit, reused on retry and reset on success: a retry
  // after a timeout (the first request may have committed) returns that session instead of
  // creating a duplicate.
  const idempotencyKeyRef = useRef<string | null>(null);
  const queryClient = useQueryClient();

  const referenceData = useReferenceData();
  const { form } = useHealingSessionForm(symptoms, initialSessionDate);
  // The session date (printed on the scan sheet): today unless the healer changes it in the scan step.
  const [sessionDate, setSessionDateState] = useState<string>(initialSessionDate ?? (form.state.values.sessionDate as string));
  const setSessionDate = useCallback(
    (value: string) => {
      setSessionDateState(value);
      form.setFieldValue("sessionDate", value);
    },
    [form]
  );

  // Typed by the healer (never generated server-side); kept as text so it can be cleared while editing.
  const [sessionNumber, setSessionNumber] = useState<string>(
    String(initialSessionNumber ?? suggestedSessionNumber ?? "")
  );

  const handleBack = useCallback(() => {
    setStep((current) => Math.max(1, current - 1));
  }, []);

  const handleNext = useCallback(async () => {
    await form.validateAllFields("change");
    const hasErrors = Object.values(form.state.fieldMeta).some(
      (meta: any) => meta.errors && meta.errors.length > 0
    );
    if (hasErrors) {
      return;
    }
    const parsedSessionNumber = Number(sessionNumber);
    if (!Number.isInteger(parsedSessionNumber) || parsedSessionNumber < 1) {
      toast.error("Enter the session number (a whole number, 1 or more)");
      return;
    }
    if (!sessionDate) {
      toast.error("Enter the session date");
      return;
    }
    if (step < FINAL_FORM_STEP) {
      setStep(step + 1);
      return;
    }
    if (isSubmitting) {
      return;
    }

    setIsSubmitting(true);

    try {
      const values = form.state.values;

      const hasProtocol =
        values.protocolName || values.referenceBook || values.detailedProtocol;
      const protocolEntries = hasProtocol
        ? [
            {
              protocolName: values.protocolName,
              referenceBook: values.referenceBook,
              detailedProtocol: values.detailedProtocol,
              colors: [],
            },
          ]
        : [];
      const hasPreScanData = preScanChakraData?.length > 0;
      const hasPostScanData = postScanChakraData?.length > 0;
      if (!hasPostScanData && !hasPreScanData) {
        toast.error(
          "Chakra Scan Data is required, please perform scanning again"
        );
        return;
      }
      const payload = constructHealingSessionPayload({
        caseId,
        sessionNumber: parsedSessionNumber,
        sessionDate: values.sessionDate,
        startTime: values.startTime,
        endTime: values.endTime,
        preScanData: preScanChakraData.map(mapScanEntry),
        postScanData: postScanChakraData.map(mapScanEntry),
        // symptomFeedbacks: values.symptomFeedbacks,
        protocolEntries,
        feedbackType: values.feedbackType,
        feedbackText: values.feedbackText,
        patientRemarks: values.patientRemarks,
      });

      if (sessionId) {
        await api.put(`/api/v1/sessions/${sessionId}`, { scans: payload.scans, sessionDate: values.sessionDate, sessionNumber: parsedSessionNumber });
        await queryClient.invalidateQueries({ queryKey: ["patients"] });
        toast.success("Session updated");
        onSaved?.();
        return;
      }

      if (!idempotencyKeyRef.current) {
        idempotencyKeyRef.current = crypto.randomUUID();
      }
      await api.post("/api/v1/sessions/case", payload, {
        headers: { "Idempotency-Key": idempotencyKeyRef.current },
      });
      idempotencyKeyRef.current = null;
      // Completing a session flips the case out of DRAFT server-side --
      // refetch so the Patients list/dashboard don't keep showing stale status.
      await queryClient.invalidateQueries({ queryKey: ["patients"] });
      await queryClient.invalidateQueries({ queryKey: ["healingCases"] });
      await queryClient.invalidateQueries({ queryKey: ["upcoming-sessions"] });
      toast.success("Session saved");
      setStep(COMPLETED_STEP);
    } catch (error) {
      toast.error(
        extractDetail(error) || "Failed to save session — please try again",
        { duration: 5000 }
      );
    } finally {
      setIsSubmitting(false);
    }
  }, [caseId, form, isSubmitting, onSaved, sessionNumber, sessionDate, postScanChakraData, preScanChakraData, queryClient, sessionId, step]);

  return {
    step,
    form,
    referenceData,
    preScanChakraData,
    postScanChakraData,
    isSubmitting,
    sessionDate,
    setSessionDate,
    sessionNumber,
    setSessionNumber,
    setPreScanChakraData,
    setPostScanChakraData,
    handleBack,
    handleNext,
  };
}
