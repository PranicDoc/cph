import { FormSchema } from "../../components/DynamicFormEngine";
import { COMMON_SYMPTOMS } from "./sessionSchema";

export const patientSessionSchema: FormSchema = [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "medicalCondition",
        label: "Chief Complaint",
        type: "textarea",
        placeholder: "Please state the main ailment that will be treated...",
        required: true,
      },
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "textarea",
        placeholder: "Please mention lab findings if any...",
      },
    ],
  },
  {
    fieldsContainerClassName: "space-y-4 mt-4",
    fields: [
      {
        name: "symptoms",
        label: "Symptoms",
        type: "tag",
        placeholder: "Type to add symptoms...",
        suggestions: COMMON_SYMPTOMS,
        displayKey: "symptomName",
        createNewTag: (name: string) => ({
          symptomName: name,
          severity: 1,
          description: "",
        }),
      },
    ],
  },
  {
    fieldsContainerClassName: "space-y-4 mt-4",
    fields: [
      {
        name: "medications",
        label: "Medications",
        type: "tag",
        placeholder: "Type to add more medications...",
        displayKey: "medicationName",
        displayFormatter: (m: any) =>
          m.dosage ? `${m.medicationName} (${m.dosage})` : m.medicationName,
        editableFields: {
          dosage: { label: "Dosage", placeholder: "e.g. 500mg" },
        },
        createNewTag: (name: string) => ({
          medicationName: name,
          dosage: "",
          frequency: "",
          notes: "",
        }),
      },
    ],
  },
  {
    containerClassName: "bg-background rounded-xl p-4 mt-4",
    fieldsContainerClassName: "grid grid-cols-2 gap-6",
    title: "Protocol Details",
    fields: [
      {
        name: "healingDetails.protocolName",
        label: "Protocol Name",
        display: false,
        wrapperClassName: "col-span-1",
      },
      {
        name: "healingDetails.detailedProtocol",
        label: "Detailed Protocol",
        type: "textarea",
        display: false,
        wrapperClassName: "row-span-2",
        rows: 4,
      },
      {
        name: "healingDetails.bookReference",
        label: "Reference Book",
        display: false,
        wrapperClassName: "col-span-1",
      },
    ],
  },
  {
    containerClassName: "bg-background rounded-xl p-4 mt-4",
    fieldsContainerClassName: "flex flex-wrap justify-around gap-4",
    title: "Book page numbers",
    fields: [
      { name: "basicBookPg", label: "Basic Book pg no", type: "number", display: false },
      { name: "advancedBookPg", label: "Advanced Book pg no", type: "number", display: false },
      { name: "psychotherapyPg", label: "Psychotherapy Book pg no", type: "number", display: false },
    ],
  },
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "complementaryDetails",
        label: "Complementary Modalities",
        type: "textarea",
        placeholder: "Please mention complementary modalities if any...",
        enableToggle: true,
      },
      {
        name: "infectiousDetails",
        label: "Infectious Illness",
        type: "textarea",
        placeholder: "Please mention infectious illness if any...",
        enableToggle: true,
      },
    ],
  },
];
