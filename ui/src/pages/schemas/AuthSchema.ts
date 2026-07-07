import { FormSchema } from "../../components/DynamicFormEngine";

export const loginSchema: FormSchema = [
  {
    fieldsContainerClassName: "space-y-4",
    fields: [
      {
        name: "email",
        label: "Email Address",
        type: "email",
        required: true,
        placeholder: "Enter your email",
      },
      {
        name: "password",
        label: "Password",
        type: "password",
        required: true,
        placeholder: "Enter your password",
      },
      {
        name: "rememberMe",
        label: "Remember me",
        type: "checkbox",
        wrapperClassName: "mt-2",
      },
    ],
  },
];

export const acceptInviteSchema: FormSchema = [
  {
    fieldsContainerClassName: "space-y-4",
    fields: [
      {
        name: "fullName",
        label: "Full Name",
        type: "text",
        required: true,
        placeholder: "Enter your full name",
      },
      {
        name: "password",
        label: "Password",
        type: "password",
        required: true,
        placeholder: "Min. 6 characters",
        validate: (value) =>
          value && value.length < 6
            ? "Password must be at least 6 characters"
            : undefined,
      },
    ],
  },
];
