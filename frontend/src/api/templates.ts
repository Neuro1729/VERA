import type {
  CompanyRegistrationRequest,
  GrantsConfigInput,
  OrganizationConfigInput,
  ResourcesConfigInput,
} from "./types";

export const ORGANIZATION_TEMPLATE: OrganizationConfigInput = {
  tenant: { id: "acme", name: "Acme Corporation" },
  structure: {
    id: "root",
    kind: "company",
    name: "Acme Corporation",
    children: [
      {
        id: "engineering",
        kind: "department",
        name: "Engineering",
        children: [
          {
            id: "backend",
            kind: "team",
            name: "Backend",
            subjects: [{ id: "emp-1001", kind: "employee", name: "Alice" }],
          },
        ],
      },
    ],
  },
};

export const RESOURCES_TEMPLATE: ResourcesConfigInput = {
  resources: [
    {
      id: "gpu",
      kind: "compute",
      name: "GPU Cluster",
      metadata: {},
      properties: {
        capacity: { type: "QUANTITY", value: 100, unit: "gpu" },
      },
      entitlementDefinitions: [
        { key: "gpu.enabled", name: "GPU Enabled", valueType: "BOOLEAN" },
        { key: "gpu.hours", name: "GPU Hours", valueType: "QUOTA" },
      ],
    },
  ],
};

export const GRANTS_TEMPLATE: GrantsConfigInput = {
  grants: [
    {
      id: "g-eng-hours",
      target: { type: "SCOPE", id: "engineering" },
      resourceId: "gpu",
      entitlementKey: "gpu.hours",
      value: { type: "QUOTA", limit: 5000, unit: "gpu-hour", period: "MONTHLY" },
    },
  ],
};

export const REGISTRATION_TEMPLATE: CompanyRegistrationRequest = {
  organization: ORGANIZATION_TEMPLATE,
  resources: RESOURCES_TEMPLATE,
  grants: GRANTS_TEMPLATE,
};

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

export function downloadJson(filename: string, value: unknown): void {
  const blob = new Blob([prettyJson(value)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
