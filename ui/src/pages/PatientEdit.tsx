import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { buildMedicalHistoryUpdate, buildPatientUpdate } from "../lib/patientUpdate";
import { parseExercise } from "../lib/exercise";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { patientDemographicsSchema, patientMedicalHistorySchema } from "./schemas/RegistrationSchemas";
import { Button } from "@/components/ui/button";
import { useUiVersion } from "../ui-version";

/** The patient as the API returns it (GET /api/v1/patients/{id}). */
export type PatientDetails = {
  id: string;
  firstName: string;
  lastName: string;
  dob: string | null;
  gender: string | null;
  maritalStatus: string | null;
  email: string | null;
  contact: string | null;
  occupation: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  zip: string | null;
};

/** The patient's medical history as the API returns it; null when none was ever saved. */
export type MedicalHistoryDetails = {
  generalHealth: string | null;
  diet: string | null;
  exercise: string | null;
  exerciseFrequency: string | null;
  hypertension: boolean | null;
  giIssues: boolean | null;
  stressDepressionAnxiety: boolean | null;
  pregnant: boolean | null;
  cancer: boolean | null;
  cancerLocation: string | null;
  cancerDetails: string | null;
  additionalNotes: string | null;
};

function PatientEditForm({ patient, history }: { patient: PatientDetails; history: MedicalHistoryDetails | null }) {
  const { isNewView } = useUiVersion();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const form = useForm({
    defaultValues: {
      firstName: patient.firstName ?? "",
      lastName: patient.lastName ?? "",
      dob: patient.dob ?? null,
      gender: patient.gender ?? null,
      maritalStatus: patient.maritalStatus ?? null,
      email: patient.email ?? null,
      contact: patient.contact ?? null,
      occupation: patient.occupation ?? null,
      addressLine1: patient.addressLine1 ?? null,
      addressLine2: patient.addressLine2 ?? null,
      city: patient.city ?? null,
      state: patient.state ?? null,
      zip: patient.zip ?? null,
      // medical history (the second registration screen)
      generalHealth: history?.generalHealth ?? null,
      diet: history?.diet ?? null,
      exercise: parseExercise(history?.exercise),
      exerciseFrequency: history?.exerciseFrequency ?? null,
      hypertension: history?.hypertension ?? false,
      giIssues: history?.giIssues ?? false,
      stressDepressionAnxiety: history?.stressDepressionAnxiety ?? false,
      pregnant: history?.pregnant ?? false,
      cancer: history?.cancer ?? false,
      cancerLocation: history?.cancerLocation ?? null,
      cancerDetails: history?.cancerDetails ?? null,
      additionalNotes: history?.additionalNotes ?? null,
    },
    onSubmit: async ({ value }) => {
      try {
        await api.put(`/api/v1/patients/${patient.id}`, buildPatientUpdate(value));
        await api.put(`/api/v1/patients/${patient.id}/medical-history`, buildMedicalHistoryUpdate(value));
        await queryClient.invalidateQueries({ queryKey: ["patients"] });
        await queryClient.invalidateQueries({ queryKey: ["patient", patient.id] });
        await queryClient.invalidateQueries({ queryKey: ["patient-history", patient.id] });
        toast.success("Patient details updated");
        navigate("/patients");
      } catch (error) {
        toast.error(extractDetail(error) || "Failed to update the patient — please try again", { duration: 5000 });
      }
    },
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className="space-y-6"
    >
      <section className="space-y-4">
        <h2 className="text-base font-semibold text-text">Personal details</h2>
        <DynamicFormEngine form={form} schema={patientDemographicsSchema} />
      </section>

      <section className="space-y-4 pt-6 border-t border-border">
        <h2 className="text-base font-semibold text-text">Medical history</h2>
        <DynamicFormEngine form={form} schema={patientMedicalHistorySchema} />
      </section>

      <div className={`flex justify-end gap-4 pt-6 border-t border-border mt-8${isNewView ? " hl-actions" : ""}`}>
        <Button
          type="button"
          variant="outline"
          onClick={() => navigate("/patients")}
          className="px-6 py-2.5 h-auto border-2 text-text"
        >
          Cancel
        </Button>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(isSubmitting) => (
            <Button type="submit" disabled={isSubmitting} className="px-6 py-2.5 h-auto">
              {isSubmitting ? "Saving…" : "Save changes"}
            </Button>
          )}
        </form.Subscribe>
      </div>
    </form>
  );
}

export default function PatientEdit() {
  const { id } = useParams();
  const { isNewView } = useUiVersion();
  const { data: patient, isLoading, isError } = useQuery<PatientDetails>({
    queryKey: ["patient", id],
    queryFn: () => api.get(`/api/v1/patients/${id}`).then((r) => r.data),
    enabled: !!id,
  });
  // A patient registered without a history has none yet (the server answers 404): start with a blank one.
  const { data: history, isLoading: historyLoading } = useQuery<MedicalHistoryDetails | null>({
    queryKey: ["patient-history", id],
    queryFn: () =>
      api
        .get(`/api/v1/patients/${id}/medical-history`)
        .then((r) => r.data)
        .catch((error) => {
          if (error?.response?.status === 404) return null;
          throw error;
        }),
    enabled: !!id,
    retry: false,
  });

  return (
    <div className={isNewView ? "hlx" : "space-y-6"}>
      <div>
        <h1 className="text-xl md:text-2xl font-bold text-text">Edit patient</h1>
        <p className="text-sm text-text-light mt-1">Update the patient's personal details.</p>
      </div>
      <div className="bg-card border border-border rounded-xl p-6 shadow-sm space-y-6">
        {(isLoading || (!!patient && historyLoading)) && <p className="text-text-light">Loading patient…</p>}
        {isError && <p className="text-red-500">Could not load this patient.</p>}
        {patient && !historyLoading && (
          <PatientEditForm key={patient.id} patient={patient} history={history ?? null} />
        )}
      </div>
    </div>
  );
}
