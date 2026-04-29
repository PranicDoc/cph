import React from 'react';
import { CustomDOBPicker } from './CustomDOBPicker';
import { ChipInputField } from './ChipInputField';

export type FieldType =
  | 'text'
  | 'date'
  | 'select'
  | 'tel'
  | 'email'
  | 'checkbox'
  | 'textarea'
  | 'password'
  | 'chip';

export type FieldConfig = {
  name: string;
  label: string;
  type?: FieldType;
  options?: { label: string; value: string }[];
  placeholder?: string;
  required?: boolean;
  className?: string;
  wrapperClassName?: string;
  labelClassName?: string;
  condition?: (values: any) => boolean;
  suggestions?: string[];
};

export type FormGroup = {
  title?: string;
  containerClassName?: string;
  fieldsContainerClassName?: string;
  fields: FieldConfig[];
};

export type FormSchema = FormGroup[];

export function DynamicField({ form, fieldConfig }: { form: any; fieldConfig: FieldConfig }) {
  const { name, label, type = 'text', options, placeholder, required, className, wrapperClassName, labelClassName } = fieldConfig;

  return (
    <form.Field
      name={name}
      validators={{
        onChange: ({ value }: any) => (required && !value ? "Required" : undefined)
      }}
      children={(field: any) => {
        if (type === 'checkbox') {
          return (
            <label className={`flex items-center gap-2 cursor-pointer ${wrapperClassName || ''}`}>
              <input
                type="checkbox"
                checked={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.checked)}
                className={`w-4 h-4 text-primary rounded border-border focus:ring-primary ${className || ''}`}
              />
              <span className={labelClassName || "text-sm"}>{label}</span>
            </label>
          );
        }


        if (type === 'chip') {
          return (
            <div className={wrapperClassName}>
              <label className="block text-sm font-semibold mb-2">{label}</label>
              <ChipInputField field={field} suggestions={fieldConfig.suggestions} />
            </div>
          );
        }

        return (
          <div className={wrapperClassName}>
            <label className={`block text-sm font-semibold text-text-light mb-2 ${labelClassName || ''}`}>
              {label} {required && '*'}
            </label>
            {type === 'select' ? (
              <select
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                className={`w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white ${className || ''}`}
              >
                <option value="">{placeholder || 'Select...'}</option>
                {options?.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            ) : type === 'textarea' ? (
              <textarea
                rows={4}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                className={`w-full p-3 border-2 border-border rounded-lg text-sm focus:border-primary outline-none ${className || ''}`}
                placeholder={placeholder}
              />
            ) : type === 'date' ? (
              <CustomDOBPicker
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                className={className}
              />
            ) : (
              <input
                type={type}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(e) => field.handleChange(e.target.value)}
                className={`w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none ${className || ''}`}
                placeholder={placeholder}
              />
            )}
            {field.state.meta.errors && field.state.meta.errors.length > 0 ? (
              <p className="text-danger text-xs mt-1">{field.state.meta.errors.join(', ')}</p>
            ) : null}
          </div>
        );
      }}
    />
  );
}

export function DynamicFormEngine({ form, schema }: { form: any; schema: FormSchema }) {
  return (
    <>
      {schema.map((group, groupIdx) => (
        <div key={groupIdx} className={group.containerClassName}>
          {group.title && <h3 className="text-sm font-semibold text-text-light uppercase tracking-wider mb-4">{group.title}</h3>}
          <div className={group.fieldsContainerClassName}>
            {group.fields.map((fieldConfig) => {
              if (fieldConfig.condition) {
                return (
                  <form.Subscribe
                    key={fieldConfig.name}
                    selector={(state: any) => state.values}
                    children={(values: any) => {
                      if (fieldConfig.condition!(values)) {
                        return <DynamicField form={form} fieldConfig={fieldConfig} />;
                      }
                      return null;
                    }}
                  />
                );
              }
              return <DynamicField key={fieldConfig.name} form={form} fieldConfig={fieldConfig} />;
            })}
          </div>
        </div>
      ))}
    </>
  );
}
