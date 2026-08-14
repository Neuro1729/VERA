import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { downloadJson, GRANTS_TEMPLATE, ORGANIZATION_TEMPLATE, prettyJson, RESOURCES_TEMPLATE } from "../../api/templates";
import { useSignupDraft } from "../../auth/SignupDraftContext";
import { JsonConfigField, parseJsonObject, readJsonFile } from "../../components/forms/JsonConfigField";
import { TopBar } from "../../components/layout/TopBar";

export function OnboardingPage() {
  const { draft, hasCredentials, setOrganizationJson, setResourcesJson, setGrantsJson } = useSignupDraft();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [orgError, setOrgError] = useState<string | null>(null);
  const [resError, setResError] = useState<string | null>(null);
  const [grantError, setGrantError] = useState<string | null>(null);

  if (!hasCredentials) return <Navigate to="/signup" replace />;

  async function onFile(
    file: File | undefined,
    setter: (value: string) => void,
    setError: (value: string | null) => void,
    next?: () => void,
  ) {
    if (!file) return;
    try {
      setter(await readJsonFile(file));
      setError(null);
      next?.();
    } catch (error) {
      setError(error instanceof Error ? error.message : "Invalid JSON file.");
    }
  }

  function continueWithSample(which: "org" | "res" | "grants") {
    if (which === "org") {
      setOrganizationJson(prettyJson(ORGANIZATION_TEMPLATE));
      setOrgError(null);
      setStep(2);
    } else if (which === "res") {
      setResourcesJson(prettyJson(RESOURCES_TEMPLATE));
      setResError(null);
      setStep(3);
    } else {
      setGrantsJson(prettyJson(GRANTS_TEMPLATE));
      setGrantError(null);
      navigate("/signup/validation");
    }
  }

  function validateAndNext(text: string, setError: (v: string | null) => void, next: () => void) {
    const parsed = parseJsonObject(text);
    if (!parsed.ok) {
      setError(parsed.error);
      return;
    }
    setError(null);
    next();
  }

  return (
    <div className="app">
      <TopBar />
      <div className="onboard">
        <div className="center">
          <div className="eyebrow">Configuration · {draft.email}</div>
          <h2>Configure VERA in three steps</h2>
          <p>Your admin credentials are ready. Now provide the three logical JSON configuration domains.</p>
        </div>
        <div className="progress">
          <div className={`prog ${step >= 1 ? "active" : ""}`} />
          <div className={`prog ${step >= 2 ? "active" : ""}`} />
          <div className={`prog ${step >= 3 ? "active" : ""}`} />
        </div>

        {step === 1 ? (
          <div className="setup">
            <div className="tag">STEP 1 OF 3</div>
            <h3>Organization structure</h3>
            <p>Tenant identity, recursive scopes and subjects.</p>
            <div className="drop">
              <div className="dropicon">O</div>
              <b>organization.json</b>
              <span>Company + hierarchy + subjects</span>
              <input
                type="file"
                accept=".json,application/json"
                style={{ marginTop: 15, fontSize: 10 }}
                onChange={(event) => void onFile(event.target.files?.[0], setOrganizationJson, setOrgError, () => setStep(2))}
              />
            </div>
            <JsonConfigField
              id="org-json"
              label="Organization JSON"
              hint="Paste or edit organization.json. Upload a file to populate this editor."
              value={draft.organizationJson}
              onChange={setOrganizationJson}
              error={orgError}
            />
            <div className="cardactions">
              <button className="btn soft" type="button" onClick={() => downloadJson("organization.json", ORGANIZATION_TEMPLATE)}>
                Download JSON template
              </button>
              <div className="actionrow">
                <button className="btn primary" type="button" onClick={() => continueWithSample("org")}>
                  Use sample & continue
                </button>
                <button className="btn" type="button" onClick={() => validateAndNext(draft.organizationJson, setOrgError, () => setStep(2))}>
                  Next
                </button>
              </div>
            </div>
          </div>
        ) : null}

        {step === 2 ? (
          <div className="setup">
            <div className="tag">STEP 2 OF 3</div>
            <h3>Resource catalog</h3>
            <p>Resources, facts and grantable entitlement definitions.</p>
            <div className="drop">
              <div className="dropicon">R</div>
              <b>resources.json</b>
              <span>Resources + properties + definitions</span>
              <input
                type="file"
                accept=".json,application/json"
                style={{ marginTop: 15, fontSize: 10 }}
                onChange={(event) => void onFile(event.target.files?.[0], setResourcesJson, setResError, () => setStep(3))}
              />
            </div>
            <JsonConfigField
              id="res-json"
              label="Resources JSON"
              hint="Paste or edit resources.json."
              value={draft.resourcesJson}
              onChange={setResourcesJson}
              error={resError}
            />
            <div className="cardactions">
              <button className="btn soft" type="button" onClick={() => downloadJson("resources.json", RESOURCES_TEMPLATE)}>
                Download JSON template
              </button>
              <div className="actionrow">
                <button className="btn" type="button" onClick={() => setStep(1)}>
                  Back
                </button>
                <button className="btn primary" type="button" onClick={() => continueWithSample("res")}>
                  Use sample & continue
                </button>
                <button className="btn" type="button" onClick={() => validateAndNext(draft.resourcesJson, setResError, () => setStep(3))}>
                  Next
                </button>
              </div>
            </div>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="setup">
            <div className="tag">STEP 3 OF 3</div>
            <h3>Grants</h3>
            <p>Assign one entitlement value to one scope/subject for one resource key.</p>
            <div className="drop">
              <div className="dropicon">G</div>
              <b>grants.json</b>
              <span>Targets + resources + entitlements + values</span>
              <input
                type="file"
                accept=".json,application/json"
                style={{ marginTop: 15, fontSize: 10 }}
                onChange={(event) => void onFile(event.target.files?.[0], setGrantsJson, setGrantError)}
              />
            </div>
            <JsonConfigField
              id="grants-json"
              label="Grants JSON"
              hint="Paste or edit grants.json."
              value={draft.grantsJson}
              onChange={setGrantsJson}
              error={grantError}
            />
            <div className="cardactions">
              <button className="btn soft" type="button" onClick={() => downloadJson("grants.json", GRANTS_TEMPLATE)}>
                Download JSON template
              </button>
              <div className="actionrow">
                <button className="btn" type="button" onClick={() => setStep(2)}>
                  Back
                </button>
                <button className="btn primary" type="button" onClick={() => continueWithSample("grants")}>
                  Use sample & continue
                </button>
                <button
                  className="btn"
                  type="button"
                  onClick={() => validateAndNext(draft.grantsJson, setGrantError, () => navigate("/signup/validation"))}
                >
                  Validate configuration
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
