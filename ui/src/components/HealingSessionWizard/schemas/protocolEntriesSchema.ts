import { FormSchema } from "../../DynamicFormEngine";

const validateProtocol = (value: any, values: any, name: string) => {
  const hasStandard =
    Boolean(values?.protocolName?.trim()) &&
    Boolean(values?.referenceBook?.trim());
  const hasDetailed = Boolean(values?.detailedProtocol?.trim());
  if (!hasStandard && !hasDetailed) {
    if (["protocolName", "referenceBook", "detailedProtocol"].includes(name))
      return "Must provide BOTH Protocol Name & Book, OR a Detailed Protocol.";
  }
  return undefined;
};

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
        validate: validateProtocol,
      },
      {
        name: "referenceBook",
        label: "Reference Book",
        type: "text",
        placeholder: "e.g., Advanced Pranic Healing",
        required: false,
        validate: validateProtocol,
      },
      {
        name: "detailedProtocol",
        label: "Or: Detailed Custom Protocol",
        type: "textarea",
        placeholder:
          "If not using a standard book protocol, describe the exact steps, chakras, and colors used here...",
        required: false,
        validate: validateProtocol,
      },
    ],
  },
];
