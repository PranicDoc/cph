import { createElement } from "react";
import type { FormGroup } from "../../components/DynamicFormEngine";
import { ChiefComplaintsField } from "../../components/caseSheet/ChiefComplaintsField";
import { PresentingIllnessField } from "../../components/caseSheet/PresentingIllnessField";
import { MAX_CHIEF_COMPLAINTS } from "../../lib/chiefComplaints";

/**
 * The two case-sheet sections shared by the healer's session form and the patient's health form, in the order
 * they appear: 1 chief complaints (problem, duration, period), then 2 presenting illness for each problem.
 * Both edit the same `chiefComplaints` form value, so the problems of section 2 are the rows of section 1.
 */
export function caseSheetComplaintFields(
  validate: (value: unknown, formValues: unknown) => string | undefined,
): FormGroup[] {
  return [
    {
      fieldsContainerClassName: "space-y-4 mt-4",
      fields: [
        {
          name: "chiefComplaints",
          label: "1. Chief complaints",
          type: "custom",
          required: true,
          helpText: `Add each problem and how long it has been going on. At least one problem is needed, up to ${MAX_CHIEF_COMPLAINTS}. Empty rows are skipped. On the PDF each one prints as "problem since 3 months".`,
          validate,
          render: (field) =>
            createElement(ChiefComplaintsField, { value: field.state.value, onChange: field.handleChange }),
        },
      ],
    },
    {
      fieldsContainerClassName: "space-y-4 mt-4",
      fields: [
        {
          name: "chiefComplaints",
          label: "2. Presenting illness",
          type: "custom",
          helpText: "Describe each problem you listed above: how it started, where it is, what it feels like, and what changes it. All answers are optional.",
          // Both sections edit one value and a field name keeps a single set of validators: the later
          // field would replace the first one's, so it carries the same rule and leaves the message to it.
          validate,
          hideError: true,
          render: (field) =>
            createElement(PresentingIllnessField, { value: field.state.value, onChange: field.handleChange }),
        },
      ],
    },
  ];
}

