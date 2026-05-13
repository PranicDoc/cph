import { FormSchema } from "../components/DynamicFormEngine";

export const sessionDetailsSchema: FormSchema = [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-3 gap-6",
    fields: [
      { name: "sessionDate", label: "Session Date", type: "date", required: true },
      { name: "startTime", label: "Start Time", type: "time", required: true },
      { name: "endTime", label: "End Time", type: "time", required: true }
    ]
  }
];

export const qualitativeFeedbackSchema: FormSchema = [
  {
    title: "Qualitative Feedback",
    containerClassName: "bg-background p-6 rounded-xl border border-border space-y-6",
    fieldsContainerClassName: "grid grid-cols-1 gap-6",
    fields: [
      {
        name: "feedbackType",
        label: "Feedback Type",
        type: "select",
        required: true,
        options: [
          { label: "Interim", value: "INTERIM" },
          { label: "Final", value: "FINAL" }
        ]
      },
      { 
        name: "feedbackText", 
        label: "Feedback Text", 
        type: "textarea", 
        required: true, 
        placeholder: "Enter detailed feedback for this session..." 
      },
      { 
        name: "patientRemarks", 
        label: "Patient Remarks (Optional)", 
        type: "textarea", 
        placeholder: "Enter any remarks provided by the patient..." 
      }
    ]
  }
];