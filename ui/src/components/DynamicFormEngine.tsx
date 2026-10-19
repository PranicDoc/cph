import React, { useCallback, useEffect, useState } from "react";
import { CustomDOBPicker } from "./CustomDOBPicker";
import { TagInput } from "./TagInput";
import { FileUpload } from "./FileUPload";
import { TableInput } from "./TableInput";
import { MultiSelect } from "@/components/ui/multi-select/multi-select";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  CheckboxField,
  FieldError,
  SelectField,
  TextField,
  TextareaField,
} from "@/components/form/Field";
import { cn } from "@/lib/utils";

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
  | "chip"
  | "table"
  | "file"
  | "multiselect";

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
  displayFormatter?: (tag: any) => string;
  editableFields?: Record<string, {
    label: string;
    placeholder?: string;
    type?: "text" | "checkbox-group";
    options?: { label: string; value: string }[];
  }>;
  createNewTag?: (input: string) => any;
  readOnly?: boolean;
  /** file fields: confirm before a file is removed. */
  confirmRemove?: boolean;
  enableToggle?: boolean;
  /** With enableToggle: once the toggle is switched on, the value must be filled in. */
  requiredWhenEnabled?: boolean;
  trimSpaces?: boolean;
  multiSelectOptions?: any[];
  getOptionKey?: (option: any) => string;
  getOptionLabel?: (option: any) => string;
  renderBadge?: (option: any) => React.ReactNode;
  multiSelectPlaceholder?: string;
  validate?: (
    value: any,
    formValues: any,
    fieldName: string
  ) => string | undefined;
};

// Blank strings and empty arrays count as "not filled in", not just falsy values.
const isEmptyValue = (value: unknown) =>
  !value ||
  (typeof value === "string" && value.trim() === "") ||
  (Array.isArray(value) && value.length === 0);

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

  useEffect(() => {
    if (!enableToggle) return;
    setEnabled(Boolean(form.state.values[name]));
  }, [form.state.values[name], enableToggle, name]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!display) return null;

  // Run for BOTH onChange and onSubmit so a required field the user never
  // touched still fails validation when the form is submitted (onChange-only
  // validators skip untouched fields, letting an empty form through).
  const runFieldValidation = ({ value }: any) => {
    if (required && isEmptyValue(value)) {
      return "Required";
    }
    if (fieldConfig.requiredWhenEnabled && enabled && isEmptyValue(value)) {
      return "Please fill this in, or switch the toggle off";
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
      return fieldConfig.validate(value, form.state.values, fieldConfig.name);
    }
    return undefined;
  };

  return (
    <form.Field
      name={name}
      validators={{
        onChange: runFieldValidation,
        onSubmit: runFieldValidation,
      }}
      children={(field: any) => {
        if (type === "checkbox") {
          return (
            <CheckboxField
              field={field}
              label={label}
              className={className}
              wrapperClassName={wrapperClassName}
              labelClassName={labelClassName}
            />
          );
        }

        if (type === "tag") {
          return (
            <div className={wrapperClassName}>
              <Label className="block text-sm font-semibold mb-2">
                {label} {required && "*"}
              </Label>
              <TagInput
                value={field.state.value}
                onChange={field.handleChange}
                suggestions={fieldConfig.suggestions}
                placeholder={fieldConfig.placeholder}
                displayKey={fieldConfig.displayKey}
                displayFormatter={fieldConfig.displayFormatter}
                valueKey={fieldConfig.valueKey}
                createNewTag={fieldConfig.createNewTag}
                editableFields={fieldConfig.editableFields}
              />
              <FieldError errors={field.state.meta.errors} />
            </div>
          );
        }

        if (type === "file") {
          return (
            <div className={wrapperClassName}>
              <Label className="block text-sm font-semibold mb-2">{label}</Label>
              <FileUpload
                value={field.state.value ?? []}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                readOnly={fieldConfig.readOnly}
                confirmRemove={fieldConfig.confirmRemove}
              />
            </div>
          );
        }

        if (type === "table") {
          if (!fieldConfig.displayKey) {
            console.error(`DynamicField: 'table' field "${name}" requires displayKey`);
            return null;
          }
          return (
            <div className={wrapperClassName}>
              <Label className="block text-sm font-semibold mb-2">{label}</Label>
              <TableInput
                value={field.state.value ?? []}
                onChange={field.handleChange}
                placeholder={fieldConfig.placeholder}
                displayKey={fieldConfig.displayKey}
                editableFields={fieldConfig.editableFields}
                createNewTag={fieldConfig.createNewTag}
                readOnly={fieldConfig.readOnly}
              />
            </div>
          );
        }

        if (type === "multiselect") {
          return (
            <div className={wrapperClassName}>
              <Label className="block text-sm font-semibold mb-2">{label}</Label>
              <MultiSelect
                options={fieldConfig.multiSelectOptions ?? []}
                value={field.state.value ?? []}
                onChange={field.handleChange}
                getOptionKey={fieldConfig.getOptionKey ?? ((o: any) => String(o))}
                getOptionLabel={fieldConfig.getOptionLabel ?? ((o: any) => String(o))}
                renderBadge={fieldConfig.renderBadge}
                placeholder={fieldConfig.multiSelectPlaceholder ?? placeholder}
                disabled={fieldConfig.readOnly}
              />
              <FieldError errors={field.state.meta.errors} />
            </div>
          );
        }

        // Toggle switch UI
        const toggleSwitch = enableToggle && (
          <Switch checked={enabled} onCheckedChange={handleToggle} />
        );

        const isDisabled = enableToggle && !enabled;

        return (
          <div className={wrapperClassName}>
            <div className="flex items-center justify-between mb-2">
              <Label
                className={cn(
                  "block text-sm font-semibold text-text-light",
                  labelClassName
                )}
              >
                {label} {required && "*"}
              </Label>
              {toggleSwitch}
            </div>

            {type === "select" ? (
              <SelectField
                field={field}
                options={options ?? []}
                placeholder={placeholder}
                disabled={isDisabled}
                className={className}
              />
            ) : type === "textarea" ? (
              <TextareaField
                field={field}
                disabled={isDisabled}
                className={className}
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
              <TextField
                field={field}
                type={type}
                trimSpaces={trimSpaces}
                disabled={isDisabled}
                className={className}
                placeholder={isDisabled ? "Enable toggle to fill" : placeholder}
              />
            )}

            <FieldError errors={field.state.meta.errors} />
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
      {schema.map((group, groupIdx) => {
        // Skip the group entirely when every field has display: false
        if (group.fields.every((f) => f.display === false)) return null;
        return (
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
                            <DynamicField
                              form={form}
                              fieldConfig={fieldConfig}
                            />
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
        );
      })}
    </>
  );
}
