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
      {
        name: "phone",
        label: "Phone Number",
        type: "text",
        placeholder: "Optional",
      },
    ],
  },
];

export const signupSchema: FormSchema = [
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
    ],
  },
];

export const resetPasswordSchema: FormSchema = [
  {
    fieldsContainerClassName: "space-y-4",
    fields: [
      {
        name: "newPassword",
        label: "New Password",
        type: "password",
        required: true,
        placeholder: "Min. 8 characters",
        validate: (value) =>
          value && value.length < 8 ? "Password must be at least 8 characters" : undefined,
      },
      {
        name: "confirmPassword",
        label: "Confirm Password",
        type: "password",
        required: true,
        placeholder: "Re-enter the new password",
        validate: (value, formValues) =>
          value && value !== formValues.newPassword ? "Passwords do not match" : undefined,
      },
    ],
  },
];
