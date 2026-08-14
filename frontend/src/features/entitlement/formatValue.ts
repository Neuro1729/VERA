import type { EntitlementValue } from "../../api/types";

export function formatEntitlementValue(value: EntitlementValue | null | undefined): string {
  if (!value) return "—";
  switch (value.type) {
    case "BOOLEAN":
      return value.value ? "Allowed" : "Denied";
    case "QUANTITY":
      return `${formatNumber(value.value)} ${value.unit}`;
    case "QUOTA":
      return `${formatNumber(value.limit)} ${value.unit} / ${value.period}`;
    case "RATE_LIMIT":
      return `${formatNumber(value.capacity)} tokens, refill ${formatNumber(value.refillTokens)} / ${value.refillPeriod}`;
    case "RANGE":
      return `${formatNumber(value.min)}–${formatNumber(value.max)} ${value.unit}`;
    case "TIME_RANGE":
      return `${formatDate(value.from)} → ${formatDate(value.until)}`;
    case "SET":
      return value.values.join(", ");
    case "TEXT":
      return value.value;
    default:
      return JSON.stringify(value);
  }
}

export function formatNumber(value: number | string): string {
  const numeric = typeof value === "number" ? value : Number(value);
  if (Number.isFinite(numeric)) return numeric.toLocaleString();
  return String(value);
}

export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function countDescendantSubjects(
  tenant: { scopes: Record<string, { subjectIds: string[]; childScopeIds: string[] }> },
  scopeId: string,
): number {
  const scope = tenant.scopes[scopeId];
  if (!scope) return 0;
  return scope.subjectIds.length + scope.childScopeIds.reduce((sum, childId) => sum + countDescendantSubjects(tenant, childId), 0);
}
