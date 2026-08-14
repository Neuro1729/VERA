import { useState, type Dispatch, type SetStateAction } from "react";
import { applyBulkSync, buildBulkSyncRequest, previewBulkSync } from "../../api/syncApi";
import { downloadJson, GRANTS_TEMPLATE, ORGANIZATION_TEMPLATE, prettyJson, RESOURCES_TEMPLATE } from "../../api/templates";
import type { BulkSyncPreview, SyncMode } from "../../api/types";
import { parseJsonObject, readJsonFile } from "../../components/forms/JsonConfigField";
import { useToast } from "../../components/common/Toast";

type Domain = "organization" | "resources" | "grants";

interface Props {
  tenantId: string;
  initialDomain?: Domain;
  onClose: () => void;
  onApplied: () => Promise<void>;
}

export function BulkWizard({ tenantId, initialDomain, onClose, onApplied }: Props) {
  const toast = useToast();
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [selected, setSelected] = useState<Set<Domain>>(new Set(initialDomain ? [initialDomain] : []));
  const [modes, setModes] = useState<Record<Domain, SyncMode>>({
    organization: "MERGE",
    resources: "MERGE",
    grants: "MERGE",
  });
  const [json, setJson] = useState<Record<Domain, string>>({
    organization: prettyJson(ORGANIZATION_TEMPLATE),
    resources: prettyJson(RESOURCES_TEMPLATE),
    grants: prettyJson(GRANTS_TEMPLATE),
  });
  const [errors, setErrors] = useState<Partial<Record<Domain, string>>>({});
  const [preview, setPreview] = useState<BulkSyncPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);

  function toggle(domain: Domain) {
    const next = new Set(selected);
    if (next.has(domain)) next.delete(domain);
    else next.add(domain);
    setSelected(next);
  }

  function parseSelected(): ReturnType<typeof buildBulkSyncRequest> {
    const parsed: Parameters<typeof buildBulkSyncRequest>[0] = {};
    for (const domain of selected) {
      const result = parseJsonObject(json[domain]);
      if (!result.ok) {
        setErrors((current) => ({ ...current, [domain]: result.error }));
        throw new Error(result.error);
      }
      parsed[domain] = { selected: true, mode: modes[domain], json: result.value };
    }
    return buildBulkSyncRequest(parsed);
  }

  async function validate() {
    setBusy(true);
    setApplyError(null);
    try {
      const request = parseSelected();
      const result = await previewBulkSync(tenantId, request);
      setPreview(result);
      setStep(3);
    } catch (error) {
      setApplyError(error instanceof Error ? error.message : "Preview failed.");
    } finally {
      setBusy(false);
    }
  }

  async function apply() {
    if (!preview?.valid) return;
    setBusy(true);
    setApplyError(null);
    try {
      const request = parseSelected();
      const result = await applyBulkSync(tenantId, request);
      if (!result.valid) {
        setPreview(result);
        setApplyError("Apply was blocked. The backend revalidated the projected tenant.");
        return;
      }
      toast.show("Bulk changes applied.");
      await onApplied();
      onClose();
    } catch (error) {
      setApplyError(error instanceof Error ? error.message : "Apply failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="overlay" role="presentation" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="bulk-title" onClick={(event) => event.stopPropagation()}>
        <div className="modalhead">
          <div>
            <h3 id="bulk-title">Bulk changes</h3>
            <p>Choose one, two or all three configuration domains.</p>
          </div>
          <button className="close" type="button" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        <div className="bulksteps">
          <div className={`bulkstep ${step >= 1 ? "active" : ""}`} />
          <div className={`bulkstep ${step >= 2 ? "active" : ""}`} />
          <div className={`bulkstep ${step >= 3 ? "active" : ""}`} />
        </div>

        {step === 1 ? (
          <>
            <div className="selectgrid">
              <DomainCard domain="organization" title="Organization" copy="Scopes, subjects, hierarchy changes." selected={selected.has("organization")} mode={modes.organization} onToggle={toggle} onMode={setModes} />
              <DomainCard domain="resources" title="Resources" copy="Catalog, properties, definitions." selected={selected.has("resources")} mode={modes.resources} onToggle={toggle} onMode={setModes} />
              <DomainCard domain="grants" title="Grants" copy="Assignments and entitlement values." selected={selected.has("grants")} mode={modes.grants} onToggle={toggle} onMode={setModes} />
            </div>
            <div className="modalactions">
              <span />
              <button
                className="btn primary"
                type="button"
                onClick={() => {
                  if (selected.size === 0) {
                    setApplyError("Choose at least one domain.");
                    return;
                  }
                  setApplyError(null);
                  setStep(2);
                }}
              >
                Continue
              </button>
            </div>
          </>
        ) : null}

        {step === 2 ? (
          <>
            {[...selected].map((domain) => (
              <div className="bulkInput" key={domain}>
                <h4>{domain[0].toUpperCase() + domain.slice(1)} changes · {modes[domain]}</h4>
                <p>
                  Provide only the JSON for this selected domain. Download a template if needed.
                  {modes[domain] === "RECONCILE" ? " Entities omitted from this selected domain may be removed." : ""}
                </p>
                <div className="inputRow">
                  <button className="btn small soft" type="button" onClick={() => downloadJson(`${domain}.json`, templateFor(domain))}>
                    Download {domain}.json template
                  </button>
                  <input
                    type="file"
                    accept=".json,application/json"
                    style={{ fontSize: 9 }}
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (!file) return;
                      void readJsonFile(file).then((text) => setJson((current) => ({ ...current, [domain]: text })));
                    }}
                  />
                </div>
                <textarea className="jsonbox" value={json[domain]} onChange={(event) => setJson((current) => ({ ...current, [domain]: event.target.value }))} />
                {errors[domain] ? <div className="formError">{errors[domain]}</div> : null}
              </div>
            ))}
            <div className="modalactions">
              <button className="btn" type="button" onClick={() => setStep(1)}>
                Back
              </button>
              <button className="btn primary" type="button" disabled={busy} onClick={() => void validate()}>
                {busy ? "Validating…" : "Validate changes"}
              </button>
            </div>
          </>
        ) : null}

        {step === 3 && preview ? (
          <>
            <div className="summaryline">
              <div className="impact">
                <b>{preview.summary.subjectsMoved}</b>
                <div>subjects moved</div>
              </div>
              <div className="impact">
                <b>{preview.summary.resourcesUpdated}</b>
                <div>resources updated</div>
              </div>
              <div className="impact">
                <b>{preview.summary.grantsAutomaticallyRemoved}</b>
                <div>grants auto-removed</div>
              </div>
              <div className="impact">
                <b style={{ color: preview.summary.invalidGrantCount ? "var(--red)" : "var(--green)" }}>{preview.summary.invalidGrantCount}</b>
                <div>invalid grants</div>
              </div>
            </div>
            <div className={`callout ${preview.valid ? "success" : "errorbox"}`}>
              <b>{preview.valid ? "Valid projected tenant." : "Invalid projected tenant."}</b>{" "}
              {preview.valid ? "Selected changes can be applied together." : "Invalid surviving grants block apply."}
            </div>
            {preview.summary.grantsAutomaticallyRemoved > 0 ? (
              <div className="callout warn">
                <b>Impact:</b> {preview.summary.grantsAutomaticallyRemoved} grants will be removed because their target/resource is intentionally removed. This is informational, not invalidity.
              </div>
            ) : null}
            {preview.issues.map((issue) => (
              <div key={`${issue.code}-${issue.entityId}`} className="tinyrow">
                <span>
                  {issue.severity} · {issue.domain} · {issue.code}
                </span>
                <span>{issue.message}</span>
              </div>
            ))}
            <div className="modalactions">
              <button className="btn" type="button" onClick={() => setStep(2)}>
                Back
              </button>
              <button className="btn primary" type="button" disabled={!preview.valid || busy} onClick={() => void apply()}>
                {busy ? "Applying…" : "Apply changes atomically"}
              </button>
            </div>
          </>
        ) : null}
        {applyError ? <div className="formError">{applyError}</div> : null}
      </div>
    </div>
  );
}

function DomainCard({
  domain,
  title,
  copy,
  selected,
  mode,
  onToggle,
  onMode,
}: {
  domain: Domain;
  title: string;
  copy: string;
  selected: boolean;
  mode: SyncMode;
  onToggle: (domain: Domain) => void;
  onMode: Dispatch<SetStateAction<Record<Domain, SyncMode>>>;
}) {
  return (
    <button type="button" className={`selectCard ${selected ? "selected" : ""}`} onClick={() => onToggle(domain)}>
      <div className="tick">✓</div>
      <b>{title}</b>
      <div>{copy}</div>
      <select
        className="mode"
        value={mode}
        onClick={(event) => event.stopPropagation()}
        onChange={(event) => onMode((current) => ({ ...current, [domain]: event.target.value as SyncMode }))}
      >
        <option>MERGE</option>
        <option>RECONCILE</option>
      </select>
    </button>
  );
}

function templateFor(domain: Domain) {
  if (domain === "organization") return ORGANIZATION_TEMPLATE;
  if (domain === "resources") return RESOURCES_TEMPLATE;
  return GRANTS_TEMPLATE;
}
