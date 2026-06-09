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

export const registerSchema: FormSchema = [
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
        placeholder: "Create a password",
      },
    ],
  },
];
