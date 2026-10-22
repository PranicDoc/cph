import { describe, expect, it } from "vitest";
import { setAllVerbs, toggleVerb } from "./PermissionsPicker";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

const roles: RbacCatalogResource = {
  resource: "ROLES",
  label: "Roles",
  verbs: [
    { value: "READ", label: "View", description: "", locked: false },
    { value: "UPDATE", label: "Edit", description: "", locked: true },
  ],
};
const patients: RbacCatalogResource = {
  resource: "PATIENTS",
  label: "Patients",
  verbs: [
    { value: "READ", label: "View", description: "", locked: false },
    { value: "CREATE", label: "Create", description: "", locked: false },
  ],
};

describe("permission picker grant helpers", () => {
  it("adds a verb in catalog order", () => {
    const g = toggleVerb({ PATIENTS: ["CREATE"] }, patients, "READ", true);
    expect(g.PATIENTS).toEqual(["READ", "CREATE"]);
  });
  it("drops the resource when its last verb is turned off", () => {
    expect(toggleVerb({ PATIENTS: ["READ"] }, patients, "READ", false)).toEqual({});
  });
  it("brings locked verbs along and drops them with the last unlocked one", () => {
    const on = toggleVerb({}, roles, "READ", true);
    expect(on.ROLES).toEqual(["READ", "UPDATE"]);
    expect(toggleVerb(on, roles, "READ", false)).toEqual({});
  });
  it("selects or clears a whole group", () => {
    expect(setAllVerbs({}, patients, true)).toEqual({ PATIENTS: ["READ", "CREATE"] });
    expect(setAllVerbs({ PATIENTS: ["READ"] }, patients, false)).toEqual({});
  });
});
