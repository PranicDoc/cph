import { useState, useEffect } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import api from "../lib/axios";
import { Skeleton } from "../components/ui/skeleton";
import { Send, CheckCircle2 } from "lucide-react";

const EMOJI_SCALE = [
  { value: 1, emoji: "😠", label: "Terrible" },
  { value: 2, emoji: "🙁", label: "Bad" },
  { value: 3, emoji: "😐", label: "Okay" },
  { value: 4, emoji: "🙂", label: "Good" },
  { value: 5, emoji: "😄", label: "Great" },
];

interface Symptom {
  caseSymptomId: string;
  name: string;
}

export default function PatientFeedback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [sessionReady, setSessionReady] = useState(false);

  const token =
    searchParams.get("token") ?? sessionStorage.getItem("feedbackToken");

  const caseId =
    searchParams.get("caseId") ?? sessionStorage.getItem("feedbackCaseId");

  const isFinal =
    searchParams.get("isFinal") ?? sessionStorage.getItem("isFinalFeedback");

  const [isInitializing, setIsInitializing] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [availableSymptoms, setAvailableSymptoms] = useState<Symptom[]>([]);
  const [symptomRatings, setSymptomRatings] = useState<Record<string, number>>(
    {}
  );
  const [feedbackText, setFeedbackText] = useState("");

  /**
   * Read token & caseId from URL once,
   * store them in sessionStorage,
   * then remove them from the URL.
   */
  useEffect(() => {
    const tokenFromUrl = searchParams.get("token");
    const caseIdFromUrl = searchParams.get("caseId");
    const isFinal = searchParams.get("isFinal");

    if (tokenFromUrl && caseIdFromUrl) {
      sessionStorage.setItem("feedbackToken", tokenFromUrl);
      sessionStorage.setItem("feedbackCaseId", caseIdFromUrl);
      sessionStorage.setItem("isFinalFeedback", isFinal);

      navigate("/feedback", {
        replace: true,
      });

      return;
    }

    setSessionReady(true);
  }, [searchParams, navigate]);

  useEffect(() => {
    if (!sessionReady) {
      return;
    }

    const initAndFetch = async () => {
      if (!token || !caseId) {
        navigate("/unauthorized");
        return;
      }

      try {
        await api.post("/api/v1/auth/init-feedback-session", {
          token,
        });
      } catch (err) {
        navigate("/unauthorized", { state: { error: err } });
        return;
      }

      setIsInitializing(false);
      setIsLoading(true);

      try {
        const res = await api.get("/api/v1/cases/symptoms", {
          params: {
            caseId,
            page: 0,
            size: 100,
          },
        });

        setAvailableSymptoms(res.data.content ?? res.data);
      } catch (err) {
        console.error("Failed to fetch symptoms:", err);
      } finally {
        setIsLoading(false);
      }
    };

    initAndFetch();
  }, [token, caseId, navigate, sessionReady]);

  const handleSymptomRatingChange = (symptomId: string, rating: number) => {
    setSymptomRatings((prev) => ({
      ...prev,
      [symptomId]: rating,
    }));
  };

  const handleSubmit = async () => {
    if (Object.keys(symptomRatings).length === 0) {
      alert("Please rate at least one symptom.");
      return;
    }

    if (!caseId) return;

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const symptomPayload = Object.entries(symptomRatings).map(
        ([caseSymptomId, emojiRating]) => ({
          caseSymptomId,
          rating: emojiRating * 2,
          notes: "",
        })
      );

      await api.post(
        `/api/v1/sessions/${caseId}/feedback/symptoms`,
        symptomPayload
      );

      if (isFinal && feedbackText.trim()) {
        await api.post(`/api/v1/sessions/${caseId}/feedback/qualitative`, {
          feedbackType: "FINAL",
          feedbackText,
          patientRemarks: "",
        });
      }
      setIsSubmitted(true);
    } catch (err) {
      if (
        err?.response?.data?.type ===
        "https://pranicdoc.com/errors/feedback-already-submitted"
      ) {
        setIsSubmitted(true);
        return;
      }
      setSubmitError(
        "Something went wrong while submitting. Please try again."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isInitializing) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="space-y-3 w-full max-w-2xl mx-auto px-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-64" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  if (isSubmitted) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen space-y-4 px-4">
        <CheckCircle2 className="w-16 h-16 text-green-500" />
        <h2 className="text-2xl font-bold text-primary-dark">Thank You!</h2>
        <p className="text-text-light text-center max-w-md">
          Your feedback has been successfully submitted. We appreciate you
          taking the time to update us on your condition.
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-8 pb-10 pt-8 px-4">
      <div>
        <h1 className="text-2xl font-bold text-primary-dark">
          Healing Feedback
        </h1>
        <p className="text-text-light text-sm mt-1">
          Please rate the symptoms you are currently experiencing.
        </p>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-6 space-y-8">
        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold text-primary-dark">
              1. Current Symptoms
            </h2>
            <p className="text-sm text-text-light">
              Rate each symptom based on how severe it feels right now.
            </p>
          </div>

          {isLoading ? (
            <div className="space-y-4">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-28 w-full rounded-xl" />
              ))}
            </div>
          ) : availableSymptoms.length > 0 ? (
            <div className="space-y-4">
              {availableSymptoms.map((symptom) => (
                <div
                  key={symptom.caseSymptomId}
                  className="border border-border rounded-xl p-4"
                >
                  <div className="mb-4">
                    <h3 className="font-medium text-primary-dark">
                      {symptom.name}
                    </h3>
                  </div>

                  <div className="flex flex-wrap justify-between gap-2">
                    {EMOJI_SCALE.map((item) => (
                      <button
                        key={item.value}
                        onClick={() =>
                          handleSymptomRatingChange(
                            symptom.caseSymptomId,
                            item.value
                          )
                        }
                        title={item.label}
                        className={`flex flex-col items-center gap-1 p-2 rounded-xl transition-all ${
                          symptomRatings[symptom.caseSymptomId] === item.value
                            ? "transform scale-110 bg-primary/10 ring-2 ring-primary"
                            : "hover:bg-primary/5 opacity-60 hover:opacity-100"
                        }`}
                      >
                        <span className="text-4xl">{item.emoji}</span>
                        <span className="text-xs text-text-light">
                          {item.label}
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-text-light italic">
              No symptoms recorded for this case.
            </p>
          )}
        </section>

        <hr className="border-border" />

        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold text-primary-dark">
              2. Additional Comments
            </h2>
            <p className="text-sm text-text-light">
              Is there anything else you'd like us to know? (Optional)
            </p>
          </div>

          <textarea
            value={feedbackText}
            onChange={(e) => setFeedbackText(e.target.value)}
            placeholder="Type your feedback here..."
            className="w-full min-h-[120px] p-3 border border-border rounded-lg text-sm text-text focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary resize-y"
          />
        </section>
      </div>

      {submitError && (
        <p className="text-sm text-red-500 text-right">{submitError}</p>
      )}

      <div className="flex justify-end">
        <button
          onClick={handleSubmit}
          disabled={isSubmitting || isLoading}
          className={`flex items-center gap-2 px-6 py-2.5 rounded-lg font-medium text-white transition-colors ${
            isSubmitting || isLoading
              ? "bg-primary/70 cursor-not-allowed"
              : "bg-primary hover:bg-primary-dark cursor-pointer"
          }`}
        >
          {isSubmitting ? (
            "Submitting..."
          ) : (
            <>
              Submit Feedback
              <Send className="w-4 h-4" />
            </>
          )}
        </button>
      </div>
    </div>
  );
}
