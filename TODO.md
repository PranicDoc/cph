@workspace Act as an expert React, TypeScript, and Vite developer. 

Before writing any code, thoroughly scan and understand the codebase. Specifically, look at `Registration.tsx` to understand the existing multi-step wizard pattern, locate the existing `ChakraScan` component, and check the backend entity structures in the `cph/app` directory (or wherever the backend models are defined).

**The Task:**
Generate a multi-step wizard component in TSX for capturing a `HealingSession`. 

**Strict Field Naming (CRITICAL):**
The form payload MUST perfectly match the backend JPA entities provided below to ensure seamless serialization. Use these exact field names in your state/form-library:
- `sessionNumber`, `sessionDate`, `startTime`, `endTime`, `status`
- `scans` (List of ChakraScan, requires `scanType`: "PRE" or "POST", `notes`, and `readings`: [{`chakraId`, `sizeReading`, `conditionReading`, `notes`}])
- `symptomFeedbacks` (List of {`caseSymptomId`, `rating`, `notes`})
- `qualitativeFeedbacks` (List of {`feedbackType`, `feedbackText`, `patientRemarks`})
- `protocolEntries` (List of {`chakraId`, `targetArea`, `technique`, `notes`, `colors`: [{`colorId`, `usageType`}]})

**Wizard Flow (Suggested):**
1. Session Details & Symptom Feedback
2. Pre-Healing Scan (Must use the existing ChakraScan component)
3. Protocol Entries
4. Post-Healing Scan (Must use the existing ChakraScan component)
5. Qualitative Feedback & Submit

**Constraints & Rules:**
1. **Reuse First:** Strictly use existing UI components (buttons, inputs, the wizard wrapper from `Registration.tsx`, and the `ChakraScan` component). DO NOT create new components unless absolutely necessary.
2. **Minimal Changes:** Keep the code footprint as small as possible. Do what is required without over-engineering.
3. **Clean Code:** Use strict TypeScript interfaces that match the backend entities. Follow standard React hooks practices. If the existing wizard uses a specific form library (like `@tanstack/react-form` or `react-hook-form`), use that exact library.
4. **Secure Code:** Ensure standard Vite/React security practices: avoid raw `dangerouslySetInnerHTML`, sanitize text area inputs (for `notes` and `feedbackText`), and ensure proper form validation (e.g., `rating` must be 0-10) before submission to prevent bad data payloads.

Generate the TSX code for the `HealingSessionWizard` following these exact constraints.