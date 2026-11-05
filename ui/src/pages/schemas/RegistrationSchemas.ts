import { FormSchema } from "../../components/DynamicFormEngine";
import { TEXT_LIMITS } from "../../lib/textLimits";
import { EXERCISE_OPTIONS } from "../../lib/exercise";

export const patientDemographicsSchema: FormSchema = [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-6",
    fields: [
      {
        name: "firstName",
        maxLength: TEXT_LIMITS.firstNameLimit,
        label: "First Name",
        required: true,
        placeholder: "Enter first name",
      },
      {
        name: "lastName",
        maxLength: TEXT_LIMITS.firstNameLimit,
        label: "Last Name",
        required: true,
        placeholder: "Enter last name",
      },
      { name: "dob", label: "Date of Birth", type: "date", required: true },
      {
        name: "gender",
        label: "Gender",
        type: "select",
        required: true,
        placeholder: "Select gender...",
        options: [
          { label: "Male", value: "M" },
          { label: "Female", value: "F" },
          { label: "Other / Non-Binary", value: "OTHER" },
        ],
      },
      {
        name: "maritalStatus",
        label: "Marital Status",
        type: "select",
        placeholder: "Select status...",
        options: [
          { label: "Single", value: "SINGLE" },
          { label: "Married", value: "MARRIED" },
          { label: "Divorced", value: "DIVORCED" },
          { label: "Widowed", value: "WIDOWED" },
          { label: "Others", value: "OTHERS" },
        ],
      },
      {
        name: "occupation",
        maxLength: TEXT_LIMITS.shortText,
        label: "Occupation",
        placeholder: "E.g. Software Engineer",
      },
      {
        name: "contact",
        label: "WhatsApp / Phone",
        type: "tel",
        required: true,
        placeholder: "+91 9876543210",
        trimSpaces: true,
      },
      {
        name: "email",
        label: "Email",
        type: "email",
        placeholder: "patient@email.com",
      },
    ],
  },
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-6 mt-6",
    fields: [
      {
        name: "addressLine1",
        maxLength: TEXT_LIMITS.note,
        label: "Address Line 1",
        placeholder: "Street address, P.O. box",
      },
      {
        name: "addressLine2",
        maxLength: TEXT_LIMITS.note,
        label: "Address Line 2",
        placeholder: "Apartment, suite, unit, building, floor, etc.",
      },
    ],
  },
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-3 gap-6 mt-6",
    fields: [
      { name: "city", label: "City", maxLength: TEXT_LIMITS.shortText },
      { name: "state", label: "State", maxLength: TEXT_LIMITS.shortText },
      { name: "zip", label: "ZIP Code" },
    ],
  },
];

export const patientMedicalHistorySchema: FormSchema = [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-6",
    fields: [
      {
        name: "generalHealth",
        label: "General Health",
        type: "select",
        required: true,
        options: [
          { label: "Excellent", value: "EXCELLENT" },
          { label: "Good", value: "GOOD" },
          { label: "Fair", value: "FAIR" },
          { label: "Poor", value: "POOR" },
        ],
      },
      {
        name: "diet",
        label: "Diet",
        required: true,
        type: "select",
        options: [
          { label: "Vegetarian", value: "VEGETARIAN" },
          // { label: "Vegan", value: "VEGAN" },
          // { label: "Non-Vegetarian", value: "NON_VEGETARIAN" },
          { label: "Mixed", value: "MIXED" },
        ],
      },
      {
        name: "exercise",
        label: "Exercise Type",
        type: "multiselect",
        required: true,
        multiSelectPlaceholder: "Select exercise types...",
        multiSelectOptions: EXERCISE_OPTIONS.map((o) => o.value),
        getOptionKey: (v: string) => v,
        getOptionLabel: (v: string) => EXERCISE_OPTIONS.find((o) => o.value === v)?.label ?? v,
      },
      {
        name: "exerciseFrequency",
        label: "Exercise Frequency",
        type: "select",
        required: true,
        options: [
          { label: "Daily", value: "DAILY" },
          { label: "Weekly", value: "WEEKLY" },
          { label: "Occasional", value: "OCCASIONAL" },
          { label: "Never", value: "NEVER" },
        ],
      },
    ],
  },
  {
    title: "Conditions",
    containerClassName:
      "bg-background p-4 rounded-xl border border-border space-y-4 mt-6",
    fieldsContainerClassName: "grid grid-cols-2 md:grid-cols-4 gap-4",
    fields: [
      { name: "hypertension", label: "Hypertension", type: "checkbox" },
      { name: "giIssues", label: "Acidity or Ulcer or any gut issues...", type: "checkbox" },
      {
        name: "stressDepressionAnxiety",
        label: "Stress / Anxiety",
        type: "checkbox",
      },
      { name: "pregnant", label: "Pregnant", type: "checkbox" },
    ],
  },
  {
    containerClassName:
      "bg-background p-4 rounded-xl border border-border space-y-4 mt-6",
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4",
    fields: [
      {
        name: "cancer",
        label: "Cancer History",
        type: "checkbox",
        labelClassName: "text-sm font-semibold",
        wrapperClassName: "col-span-full",
      },
      {
        name: "cancerLocation",
        maxLength: TEXT_LIMITS.shortText,
        label: "Cancer Location",
        placeholder: "E.g. Breast, Lung",
        condition: (values) => values.cancer,
      },
      {
        name: "cancerDetails",
        maxLength: TEXT_LIMITS.historyBox,
        label: "Cancer Details",
        placeholder: "Stage, current treatment, etc.",
        condition: (values) => values.cancer,
      },
    ],
  },
  {
    fieldsContainerClassName: "mt-6",
    fields: [
      {
        name: "additionalNotes",
        maxLength: TEXT_LIMITS.historyBox,
        label: "Additional Notes",
        type: "textarea",
        placeholder: "Any other relevant medical history...",
      },
    ],
  },
];

/**
 * The patient edit page: the medical history with the declaration date straight after the exercise
 * fields. Registration shares the medical history schema, so the date is added only here.
 */
export const patientEditSchema = ({
  required,
  readOnly,
}: {
  required: boolean;
  readOnly: boolean;
}): FormSchema =>
  patientMedicalHistorySchema.map((group) => ({
    ...group,
    fields: group.fields.flatMap((field) =>
      field.name === "exerciseFrequency"
        ? [
            field,
            {
              name: "declarationDate",
              label: readOnly ? "Declaration date (read-only until the case is unlocked)" : "Declaration date",
              type: "date" as const,
              required,
              readOnly,
            },
          ]
        : [field]
    ),
  }));

/**
 * `onBehalf`: a healer is filling the form in for the patient. They must confirm they have the
 * patient's consent to do so, and the signature is the patient's name, filled in automatically.
 */
export const consentDeclarationSchema = (
  patientName: string,
  { onBehalf = false }: { onBehalf?: boolean } = {}
): FormSchema => [
  {
    title: "Patient Consent & Declaration",
    containerClassName:
      "bg-background p-6 rounded-xl border border-border space-y-6",
    fieldsContainerClassName: "space-y-4",
    fields: [
      ...(onBehalf
        ? [
            {
              name: "healerConsentAttestation",
              label:
                "I confirm that I have got the consent from the patient to fill in these details on their behalf.",
              type: "checkbox" as const,
              required: true,
            },
          ]
        : []),
      {
        name: "consent1",
        label:
          "I understand that Pranic Healing is not meant to replace conventional medicine but rather to compliment it.",
        type: "checkbox",
        required: true,
      },
      {
        name: "consent2",
        label:
          "I hereby release the person or organizations providing Pranic Healing from any liabilities as a result of the services received by me.",
        type: "checkbox",
        required: true,
      },
      {
        name: "consent3",
        label:
          "I confirm that I have obtained the patient's consent to use this testimonial for my Pranic Healing Certification program.",
        type: "checkbox",
        required: true,
      },
    ],
  },
  {
    title: "Signatures",
    containerClassName:
      "bg-background p-6 rounded-xl border border-border space-y-6 mt-6",
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-6",
    fields: [
      {
        name: "patientSignature",
        maxLength: TEXT_LIMITS.note,
        label: onBehalf ? "Patient Signature (filled in automatically)" : "Patient Signature (Type Full Name)",
        type: "text",
        required: true,
        readOnly: onBehalf,
        placeholder: "Enter patient's full name",
        validate: (value) => value != patientName ? "Signature mismatch" : undefined
      },
      ...(onBehalf
        ? [{ name: "declarationDate", label: "Declaration date", type: "date" as const, required: true }]
        : []),
    ],
  },
];
