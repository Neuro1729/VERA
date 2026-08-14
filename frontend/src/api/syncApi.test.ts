import { expect, test } from "vitest";
import { buildBulkSyncRequest } from "./syncApi";
import { ORGANIZATION_TEMPLATE, RESOURCES_TEMPLATE, GRANTS_TEMPLATE } from "./templates";

test("one selected domain builds correct request", () => {
  const request = buildBulkSyncRequest({
    organization: { selected: true, mode: "MERGE", json: ORGANIZATION_TEMPLATE },
  });
  expect(request.organization?.mode).toBe("MERGE");
  expect(request.organization?.structure.id).toBe("root");
  expect(request.resources).toBeUndefined();
  expect(request.grants).toBeUndefined();
});

test("two selected domains builds correct request", () => {
  const request = buildBulkSyncRequest({
    organization: { selected: true, mode: "MERGE", json: ORGANIZATION_TEMPLATE },
    resources: { selected: true, mode: "RECONCILE", json: RESOURCES_TEMPLATE },
  });
  expect(request.organization?.mode).toBe("MERGE");
  expect(request.resources?.mode).toBe("RECONCILE");
  expect(request.grants).toBeUndefined();
});

test("all three selected domains build correct request", () => {
  const request = buildBulkSyncRequest({
    organization: { selected: true, mode: "MERGE", json: ORGANIZATION_TEMPLATE },
    resources: { selected: true, mode: "MERGE", json: RESOURCES_TEMPLATE },
    grants: { selected: true, mode: "RECONCILE", json: GRANTS_TEMPLATE },
  });
  expect(request.organization).toBeTruthy();
  expect(request.resources).toBeTruthy();
  expect(request.grants?.mode).toBe("RECONCILE");
  expect(request.grants?.grants[0].id).toBe("g-eng-hours");
});

test("independent MERGE/RECONCILE modes are preserved", () => {
  const request = buildBulkSyncRequest({
    organization: { selected: true, mode: "RECONCILE", json: { structure: ORGANIZATION_TEMPLATE.structure } },
    grants: { selected: true, mode: "MERGE", json: GRANTS_TEMPLATE },
  });
  expect(request.organization?.mode).toBe("RECONCILE");
  expect(request.grants?.mode).toBe("MERGE");
});
