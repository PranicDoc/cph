import React, { useCallback, useEffect, useState } from "react";
import { CustomDOBPicker } from "./CustomDOBPicker";
import { TagInput } from "./TagInput";

export type FieldType =
  | "text"
  | "date"
  | "select"
  | "tel"
  | "email"
  | "checkbox"
  | "textarea"
  | "password"
  | "number"
  | "tag"
  | "time"
  | "chip";

export type FieldConfig = {
  name: string;
  label: string;
  display?: boolean;
  type?: FieldType;
  options?: { label: string; value: string }[];
  placeholder?: string;
  required?: boolean;
  className?: string;
  wrapperClassName?: string;
  labelClassName?: string;
  condition?: (values: any) => boolean;
  suggestions?: string[];
  rows?: number;
  valueKey?: string;
  displayKey?: string;
  createNewTag?: (input: string) => any;
  enableToggle?: boolean;
  trimSpaces?: boolean;
  validate?: (
    value: any,
    formValues: any,
    fieldName: string
  ) => string | undefined;
};

export type FormGroup = {
  title?: string;
  containerClassName?: string;
  fieldsContainerClassName?: string;
  fields: FieldConfig[];
};

export type FormSchema = FormGroup[];

export function DynamicField({
  form,
  fieldConfig,
}: {
  form: any;
  fieldConfig: FieldConfig;
}) {
  const {
    name,
    label,
    display = true,
    type = "text",
    options,
    placeholder,
    required,
    className,
    wrapperClassName,
    labelClassName,
    enableToggle = false,
    trimSpaces = false,
  } = fieldConfig;

  const [enabled, setEnabled] = useState(false);

  const handleToggle = useCallback(() => {
    setEnabled((prev) => {
      const next = !prev;
      if (!next) {
        form.setFieldValue(name, "");
      }
      return next;
    });
  }, [form, name]);

  if (!display) return null;

  return (
    <form.Field
      name={name}
      validators={{
        onChange: ({ value }: any) => {
          if (required && !value) {
            return "Required";
          }
          // Contact number validation
          if (name === "contact" && value) {
            const normalized = value.replace(/\s+/g, "");

            const phoneRegex = /^\+?\d{10,15}$/;

            if (!phoneRegex.test(normalized)) {
              return "Invalid contact number";
            }
          }
          if (fieldConfig.validate) {
            return fieldConfig.validate(
              value,
              form.state.values,
              fieldConfig.name
            );
          }
          return undefined;
        },
      }}
      children={(field: any) => {
        if (type === "checkbox") {
          return (
            <label
              className={`flex items-center gap-2 cursor-pointer ${
                wrapperClassName || ""
              }`}
            >
              <input
                type="checkbox"
                checked={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.checked)}
                className={`w-4 h-4 text-primary rounded border-border focus:ring-primary ${
                  className || ""
                }`}
              />
              <span className={labelClassName || "text-sm"}>{label}</span>
            </label>
          );
        }

        if (type === "tag") {
          return (
            <div className={wrapperClassName}>
              <label className="block text-sm font-semibold mb-2">
                {label}
              </label>
              <TagInput
                value={field.state.value}
                onChange={field.handleChange}
                suggestions={fieldConfig.suggestions}
                placeholder={fieldConfig.placeholder}
                displayKey={fieldConfig.displayKey}
                valueKey={fieldConfig.valueKey}
                createNewTag={fieldConfig.createNewTag}
              />
            </div>
          );
        }

        // Toggle switch UI
        const toggleSwitch = enableToggle && (
          <button
            type="button"
            onClick={handleToggle}
            className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary/50 ${
              enabled ? "bg-primary" : "bg-border"
            }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                enabled ? "translate-x-4" : "translate-x-0.5"
              }`}
            />
          </button>
        );

        const isDisabled = enableToggle && !enabled;

        return (
          <div className={wrapperClassName}>
            <div className="flex items-center justify-between mb-2">
              <label
                className={`block text-sm font-semibold text-text-light ${
                  labelClassName || ""
                }`}
              >
                {label} {required && "*"}
              </label>
              {toggleSwitch}
            </div>

            {type === "select" ? (
              <select
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                disabled={isDisabled}
                className={`w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white ${
                  isDisabled ? "opacity-50 cursor-not-allowed bg-gray-50" : ""
                } ${className || ""}`}
              >
                <option value="">{placeholder || "Select..."}</option>
                {options?.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            ) : type === "textarea" ? (
              <textarea
                rows={4}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                disabled={isDisabled}
                className={`w-full p-3 border-2 border-border rounded-lg text-sm focus:border-primary outline-none ${
                  isDisabled ? "opacity-50 cursor-not-allowed bg-gray-50" : ""
                } ${className || ""}`}
                placeholder={isDisabled ? "Enable toggle to fill" : placeholder}
              />
            ) : type === "date" ? (
              <CustomDOBPicker
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                disabled={isDisabled}
                className={className}
              />
            ) : (
              <input
                type={type}
                value={
                  trimSpaces
                    ? field.state.value.replace(/\s+/g, "")
                    : field.state.value
                }
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                disabled={isDisabled}
                className={`w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none ${
                  isDisabled ? "opacity-50 cursor-not-allowed bg-gray-50" : ""
                } ${className || ""}`}
                placeholder={isDisabled ? "Enable toggle to fill" : placeholder}
              />
            )}

            {field.state.meta.errors && field.state.meta.errors.length > 0 ? (
              <p className="text-danger text-xs mt-1">
                {field.state.meta.errors.join(", ")}
              </p>
            ) : null}
          </div>
        );
      }}
    />
  );
}

export function DynamicFormEngine({
  form,
  schema,
}: {
  form: any;
  schema: FormSchema;
}) {
  return (
    <>
      {schema.map((group, groupIdx) => (
        <div key={groupIdx} className={group.containerClassName}>
          {group.title && (
            <h3 className="text-sm font-semibold text-text-light uppercase tracking-wider mb-4">
              {group.title}
            </h3>
          )}
          <div className={group.fieldsContainerClassName}>
            {group.fields.map((fieldConfig) => {
              if (fieldConfig.condition) {
                return (
                  <form.Subscribe
                    key={fieldConfig.name}
                    selector={(state: any) => state.values}
                    children={(values: any) => {
                      if (fieldConfig.condition!(values)) {
                        return (
                          <DynamicField form={form} fieldConfig={fieldConfig} />
                        );
                      }
                      return null;
                    }}
                  />
                );
              }
              return (
                <DynamicField
                  key={fieldConfig.name}
                  form={form}
                  fieldConfig={fieldConfig}
                />
              );
            })}
          </div>
        </div>
      ))}
    </>
  );
}
