import { FormSchema } from "../components/DynamicFormEngine";

export const sessionFormSchema: FormSchema = [
    {
        fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-6",
        fields: [
            {
                name: "generalHealth",
                label: "General Health",
                type: "select",
                options: [
                    { label: "Excellent", value: "EXCELLENT" },
                    { label: "Good", value: "GOOD" },
                ],
            },
            {
                name: "diet",
                label: "Diet",
                type: "select",
                options: [
                    { label: "Veg", value: "VEG" },
                    { label: "Non-Veg", value: "NON_VEG" },
                ],
            },
            { name: "exercise", label: "Exercise" },
            {
                name: "exerciseFrequency",
                label: "Exercise Frequency",
                type: "select",
                options: [
                    { label: "Daily", value: "DAILY" },
                    { label: "Weekly", value: "WEEKLY" },
                ],
            },
        ],
    },

    {
        title: "Conditions",
        containerClassName: "mt-6",
        fieldsContainerClassName: "grid grid-cols-2 gap-4",
        fields: [
            { name: "hypertension", label: "Hypertension", type: "checkbox" },
            { name: "giIssues", label: "GI Issues", type: "checkbox" },
            { name: "stressDepressionAnxiety", label: "Stress", type: "checkbox" },
            { name: "pregnant", label: "Pregnant", type: "checkbox" },
        ],
    },

    {
        title: "Session Details",
        containerClassName: "mt-6",
        fieldsContainerClassName: "space-y-4",
        fields: [
            {
                name: "symptoms",
                label: "Symptoms",
                type: "chip",
                suggestions: ["Headache", "Fever", "Fatigue"],
            },
            {
                name: "protocols",
                label: "Protocols",
                type: "chip",
            },
            {
                name: "medications",
                label: "Medications",
                type: "chip",
            },
        ],
    },

    {
        fieldsContainerClassName: "mt-6",
        fields: [
            {
                name: "additionalNotes",
                label: "Additional Notes",
                type: "textarea",
            },
        ],
    },
];