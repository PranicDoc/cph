import { FormSchema } from "../../DynamicFormEngine";

export const protocolDetailsSchema: FormSchema = [
  {
    title: "Treatment Protocol",
    containerClassName:
      "bg-background p-6 rounded-xl border border-border space-y-6",
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-6",
    fields: [
      {
        name: "protocolName",
        label: "Protocol Name",
        type: "text",
        placeholder: "e.g., Asthma Protocol",
        required: false,
      },
      {
        name: "referenceBook",
        label: "Reference Book",
        type: "text",
        placeholder: "e.g., Advanced Pranic Healing",
        required: false,
      },
      {
        name: "detailedProtocol",
        label: "Or: Detailed Custom Protocol",
        type: "textarea",
        placeholder:
          "If not using a standard book protocol, describe the exact steps, chakras, and colors used here...",
        required: false,
      },
    ],
  },
];
