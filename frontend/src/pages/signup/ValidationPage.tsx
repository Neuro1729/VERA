import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { signup } from "../../api/authApi";
import { ApiClientError } from "../../api/client";
import { previewRegistration } from "../../api/registrationApi";
import type { CompanyRegistrationRequest, RegistrationPreview } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { useSignupDraft } from "../../auth/SignupDraftContext";
import { TopBar } from "../../components/layout/TopBar";

function parseDomain(text: string, name: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new Error(`${name} is not valid JSON.`);
  }
}

function buildRequest(orgText: string, resText: string, grantsText: string): CompanyRegistrationRequest {
  const organization = parseDomain(orgText, "Organization") as CompanyRegistrationRequest["organization"];
  const resourcesRaw = parseDomain(resText, "Resources");
  const grantsRaw = parseDomain(grantsText, "Grants");
  const resources =
    resourcesRaw && typeof resourcesRaw === "object" && "resources" in resourcesRaw
      ? (resourcesRaw as CompanyRegistrationRequest["resources"])
      : { resources: resourcesRaw as CompanyRegistrationRequest["resources"]["resources"] };
  const grants =
    grantsRaw && typeof grantsRaw === "object" && "grants" in grantsRaw
      ? (grantsRaw as CompanyRegistrationRequest["grants"])
      : { grants: grantsRaw as CompanyRegistrationRequest["grants"]["grants"] };
  return { organization, resources, grants };
}

export function ValidationPage() {
  const { draft, hasCredentials, clearDraft } = useSignupDraft();
  const { setUser } = useAuth();
  const navigate = useNavigate();
  const [preview, setPreview] = useState<RegistrationPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState(false);

  useEffect(() => {
    if (!hasCredentials) return;
    let cancelled = false;
    (async () => {
      try {
        const request = buildRequest(draft.organizationJson, draft.resourcesJson, draft.grantsJson);
        const result = await previewRegistration(request);
        if (!cancelled) setPreview(result);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Preview failed.");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [draft.grantsJson, draft.organizationJson, draft.resourcesJson, hasCredentials]);

  if (!hasCredentials && !created) return <Navigate to="/signup" replace />;

  async function createWorkspace() {
    setBusy(true);
    setError(null);
    try {
      const registration = buildRequest(draft.organizationJson, draft.resourcesJson, draft.grantsJson);
      const result = await signup({
        admin: { email: draft.email, password: draft.password },
        registration,
      });
      setCreated(true);
      setUser({ email: result.admin.email, tenantId: result.tenantId });
      navigate("/signup/api-key", { replace: true, state: { apiKey: result.apiKey, tenantId: result.tenantId } });
      clearDraft();
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Signup failed.");
    } finally {
      setBusy(false);
    }
  }

  const issues = preview?.issues ?? [];
  const domainStatus = (domain: "ORGANIZATION" | "RESOURCES" | "GRANTS") => {
    const domainIssues = issues.filter((issue) => issue.domain === domain);
    const errors = domainIssues.filter((issue) => issue.severity === "ERROR");
    if (errors.length) return { ok: false, label: "FAILED", detail: errors[0].message };
    if (domainIssues.length) return { ok: true, label: "WARNINGS", detail: domainIssues[0].message };
    return { ok: true, label: "PASSED", detail: "Unique IDs · valid references" };
  };

  const org = domainStatus("ORGANIZATION");
  const res = domainStatus("RESOURCES");
  const grants = domainStatus("GRANTS");
  const crossOk = (preview?.summary.invalidGrantCount ?? 0) === 0 && (preview?.summary.errorCount ?? 0) === 0;

  return (
    <div className="app">
      <TopBar />
      <div className="validation">
        <div className="center">
          <div className="eyebrow">Validation</div>
          <h2>{preview?.valid ? "Everything looks good" : "Projected tenant needs attention"}</h2>
          <p>VERA validates the projected final tenant before anything is registered.</p>
        </div>
        {error ? <div className="callout errorbox">{error}</div> : null}
        {!preview && !error ? <div className="loading">Validating configuration…</div> : null}
        {preview ? (
          <div className="vcard">
            <CheckRow title="Organization structure" ok={org.ok} label={org.label} sub={org.detail} />
            <CheckRow title="Resources" ok={res.ok} label={res.label} sub={res.detail} />
            <CheckRow title="Grants" ok={grants.ok} label={grants.label} sub={grants.detail} />
            <CheckRow
              title="Cross-domain validation"
              ok={crossOk}
              label={crossOk ? "PASSED" : "FAILED"}
              sub={`${preview.summary.invalidGrantCount} invalid surviving grants · ${preview.summary.errorCount} errors · ${preview.summary.warningCount} warnings`}
            />
            {issues.map((issue) => (
              <div key={`${issue.code}-${issue.entityId}`} className="tinyrow">
                <span>
                  {issue.severity} · {issue.domain} · {issue.code}
                </span>
                <span>
                  {issue.message}
                  {issue.relatedEntityIds.length ? ` (${issue.relatedEntityIds.join(", ")})` : ""}
                </span>
              </div>
            ))}
            <div className={`validsummary ${preview.valid ? "" : "invalid"}`}>
              <b>{preview.valid ? "Ready to register." : "Registration is blocked."}</b> {preview.summary.scopeCount} scopes ·{" "}
              {preview.summary.subjectCount} subjects · {preview.summary.resourceCount} resources ·{" "}
              {preview.summary.entitlementDefinitionCount} entitlement definitions · {preview.summary.grantCount} grants.
            </div>
            <div className="right">
              <button className="btn" type="button" onClick={() => navigate("/signup/configuration")}>
                Back
              </button>
              <button className="btn primary" type="button" disabled={!preview.valid || busy} onClick={() => void createWorkspace()}>
                {busy ? "Creating…" : "Create workspace & API key"}
              </button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function CheckRow({ title, sub, ok, label }: { title: string; sub: string; ok: boolean; label: string }) {
  return (
    <div className="checkrow">
      <div className={`checkicon ${ok ? "" : "error"}`}>{ok ? "✓" : "!"}</div>
      <div>
        <div className="checktitle">{title}</div>
        <div className="checksub">{sub}</div>
      </div>
      <div className={ok ? "pass" : "fail"}>{label}</div>
    </div>
  );
}
