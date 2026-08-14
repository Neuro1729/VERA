import { useState } from "react";
import { executeCommand } from "../../api/commandApi";
import { fetchResourceLive } from "../../api/resourceApi";
import type {
  EntitlementDefinition,
  EntitlementGrant,
  EntitlementValue,
  EntitlementValueType,
  GrantLive,
  Resource,
  ResourceLiveResult,
  Tenant,
} from "../../api/types";
import { Modal } from "../../components/common/Modal";
import { useToast } from "../../components/common/Toast";
import { formatEntitlementValue, formatNumber } from "../entitlement/formatValue";

interface Props {
  tenant: Tenant;
  onChanged: () => Promise<void>;
}

export function ResourceExplorer({ tenant, onChanged }: Props) {
  const resources = Object.values(tenant.resources);
  const [adding, setAdding] = useState(false);
  if (resources.length === 0) {
    return (
      <div>
        <div className="empty">No resources yet.</div>
        <button className="btn primary" type="button" onClick={() => setAdding(true)}>
          Add resource
        </button>
        {adding ? <ResourceForm tenant={tenant} onClose={() => setAdding(false)} onChanged={onChanged} /> : null}
      </div>
    );
  }
  return (
    <div>
      <div className="inlineActions" style={{ marginBottom: 12 }}>
        <button className="btn small primary" type="button" onClick={() => setAdding(true)}>
          Add resource
        </button>
      </div>
      {resources.map((resource) => (
        <ResourceCard key={resource.id} tenant={tenant} resource={resource} onChanged={onChanged} />
      ))}
      {adding ? <ResourceForm tenant={tenant} onClose={() => setAdding(false)} onChanged={onChanged} /> : null}
    </div>
  );
}

function ResourceCard({ tenant, resource, onChanged }: { tenant: Tenant; resource: Resource; onChanged: () => Promise<void> }) {
  const [open, setOpen] = useState(false);
  const [live, setLive] = useState<ResourceLiveResult | null>(null);
  const [mode, setMode] = useState<null | "edit" | "remove" | "grant">(null);
  const grants = Object.values(tenant.grants).filter((grant) => grant.resourceId === resource.id);

  async function expand() {
    const next = !open;
    setOpen(next);
    if (next && !live) {
      setLive(await fetchResourceLive(tenant.id, resource.id));
    }
  }

  return (
    <div className="resourceCard">
      <button className="rowbtn" type="button" aria-expanded={open} onClick={() => void expand()}>
        <div className="leftlabel">
          <div className="chev">{open ? "▾" : "›"}</div>
          <div>
            <div className="rowtitle">{resource.name}</div>
            <div className="rowsub">
              {resource.kind} · {resource.entitlementDefinitions.length} entitlement definitions
            </div>
          </div>
        </div>
        <span className="rowsub">{resource.id}</span>
      </button>
      <div className={`expand ${open ? "open" : ""}`}>
        <div className="inlineActions">
          <button className="btn small" type="button" onClick={() => setMode("edit")}>
            Edit resource
          </button>
          <button className="btn small" type="button" onClick={() => setMode("grant")}>
            Set entitlement grant
          </button>
          <button className="btn small danger" type="button" onClick={() => setMode("remove")}>
            Remove resource
          </button>
        </div>
        {resource.entitlementDefinitions.map((definition) => (
          <EntitlementBlock
            key={definition.key}
            tenant={tenant}
            resource={resource}
            definition={definition}
            grants={grants.filter((grant) => grant.entitlementKey === definition.key)}
            live={live?.entitlements.find((item) => item.entitlementKey === definition.key)?.grants ?? []}
            onChanged={onChanged}
          />
        ))}
      </div>
      {mode === "edit" ? <ResourceForm tenant={tenant} resource={resource} onClose={() => setMode(null)} onChanged={onChanged} /> : null}
      {mode === "grant" ? (
        <GrantForm tenant={tenant} resource={resource} onClose={() => setMode(null)} onChanged={onChanged} />
      ) : null}
      {mode === "remove" ? (
        <ConfirmRemove
          message="Removing this resource cascade-purges its grants."
          onClose={() => setMode(null)}
          onConfirm={async () => {
            await executeCommand({ type: "REMOVE_RESOURCE", tenantId: tenant.id, payload: { resourceId: resource.id } });
          }}
          onChanged={onChanged}
        />
      ) : null}
    </div>
  );
}

function EntitlementBlock({
  tenant,
  resource,
  definition,
  grants,
  live,
  onChanged,
}: {
  tenant: Tenant;
  resource: Resource;
  definition: EntitlementDefinition;
  grants: EntitlementGrant[];
  live: GrantLive[];
  onChanged: () => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="entitlement">
      <button className="entitlementHead" type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <div>
          <div className="rowtitle">{definition.key}</div>
          <div className="rowsub">
            {definition.valueType} · {definition.name}
          </div>
        </div>
        <span className="rowsub">{grants.length} grants</span>
      </button>
      <div className={`entitlementBody ${open ? "open" : ""}`}>
        {grants.map((grant) => (
          <GrantBlock
            key={grant.id}
            tenant={tenant}
            resource={resource}
            grant={grant}
            live={live.find((item) => item.grantId === grant.id)}
            onChanged={onChanged}
          />
        ))}
      </div>
    </div>
  );
}

function GrantBlock({
  tenant,
  resource,
  grant,
  live,
  onChanged,
}: {
  tenant: Tenant;
  resource: Resource;
  grant: EntitlementGrant;
  live?: GrantLive;
  onChanged: () => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const targetName =
    grant.target.type === "SCOPE"
      ? tenant.scopes[grant.target.id]?.name ?? grant.target.id
      : tenant.subjects[grant.target.id]?.name ?? grant.target.id;
  return (
    <div className="grant">
      <button className="grantHead" type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <div>
          <div className="rowtitle">{grant.id}</div>
          <div className="rowsub">
            {targetName} · {formatEntitlementValue(grant.value)}
          </div>
        </div>
        <span className="rowsub">live users</span>
      </button>
      <div className={`grantBody ${open ? "open" : ""}`}>
        <div className="rowsub">Subjects currently resolving to this grant</div>
        <div className="liveusers">
          <span className="userpill">{live ? `${live.entitledSubjectCount} entitled subjects` : "Expand resource to load live counts"}</span>
          {live?.active ? <span className="userpill">active</span> : <span className="userpill">inactive</span>}
        </div>
        {live?.runtime?.consumed !== undefined ? (
          <div className="tinyrow">
            <span>Consumed</span>
            <span>
              {formatNumber(live.runtime.consumed)} / {live.runtime.limit !== undefined ? formatNumber(live.runtime.limit) : "—"}
            </span>
          </div>
        ) : null}
        <div className="inlineActions">
          <button
            className="btn small danger"
            type="button"
            onClick={() => {
              void (async () => {
                await executeCommand({
                  type: "REMOVE_ENTITLEMENT",
                  tenantId: tenant.id,
                  payload: { target: grant.target, resourceId: resource.id, entitlementKey: grant.entitlementKey },
                });
                await onChanged();
              })();
            }}
          >
            Remove grant
          </button>
        </div>
      </div>
    </div>
  );
}

function ResourceForm({
  tenant,
  resource,
  onClose,
  onChanged,
}: {
  tenant: Tenant;
  resource?: Resource;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [id, setId] = useState(resource?.id ?? "");
  const [kind, setKind] = useState(resource?.kind ?? "");
  const [name, setName] = useState(resource?.name ?? "");
  const [definitions, setDefinitions] = useState(
    JSON.stringify(resource?.entitlementDefinitions ?? [{ key: "", name: "", valueType: "BOOLEAN" }], null, 2),
  );
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal title={resource ? "Edit resource" : "Add resource"} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void (async () => {
            try {
              const entitlementDefinitions = JSON.parse(definitions) as EntitlementDefinition[];
              if (resource) {
                await executeCommand({
                  type: "UPDATE_RESOURCE",
                  tenantId: tenant.id,
                  payload: {
                    resourceId: resource.id,
                    kind,
                    name,
                    metadata: resource.metadata,
                    properties: resource.properties,
                    entitlementDefinitions,
                    replace: true,
                  },
                });
              } else {
                await executeCommand({
                  type: "ADD_RESOURCE",
                  tenantId: tenant.id,
                  payload: {
                    resource: {
                      id,
                      kind,
                      name,
                      metadata: {},
                      properties: {},
                      entitlementDefinitions,
                    },
                  },
                });
              }
              await onChanged();
              toast.show("Resource saved.");
              onClose();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Invalid resource.");
            }
          })();
        }}
      >
        {!resource ? (
          <div className="field">
            <label htmlFor="res-id">ID</label>
            <input id="res-id" value={id} onChange={(e) => setId(e.target.value)} required />
          </div>
        ) : null}
        <div className="field">
          <label htmlFor="res-kind">Kind</label>
          <input id="res-kind" value={kind} onChange={(e) => setKind(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="res-name">Name</label>
          <input id="res-name" value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="res-defs">Entitlement definitions JSON</label>
          <textarea id="res-defs" className="jsonbox" value={definitions} onChange={(e) => setDefinitions(e.target.value)} />
        </div>
        {error ? <div className="formError">{error}</div> : null}
        <div className="modalactions">
          <button className="btn" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" type="submit">
            Save
          </button>
        </div>
      </form>
    </Modal>
  );
}

function GrantForm({
  tenant,
  resource,
  onClose,
  onChanged,
}: {
  tenant: Tenant;
  resource: Resource;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [grantId, setGrantId] = useState("");
  const [targetType, setTargetType] = useState<"SCOPE" | "SUBJECT">("SCOPE");
  const [targetId, setTargetId] = useState("");
  const [entitlementKey, setEntitlementKey] = useState(resource.entitlementDefinitions[0]?.key ?? "");
  const definition = resource.entitlementDefinitions.find((item) => item.key === entitlementKey);
  const [value, setValue] = useState<EntitlementValue>({ type: "BOOLEAN", value: true });
  const [error, setError] = useState<string | null>(null);

  return (
    <Modal title="Set entitlement grant" onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void (async () => {
            try {
              await executeCommand({
                type: "SET_ENTITLEMENT",
                tenantId: tenant.id,
                payload: {
                  grantId,
                  target: { type: targetType, id: targetId },
                  resourceId: resource.id,
                  entitlementKey,
                  value: typedValue(definition?.valueType ?? "BOOLEAN", value),
                },
              });
              await onChanged();
              toast.show("Grant saved.");
              onClose();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Grant failed.");
            }
          })();
        }}
      >
        <div className="field">
          <label htmlFor="grant-id">Grant ID</label>
          <input id="grant-id" value={grantId} onChange={(e) => setGrantId(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="target-type">Target type</label>
          <select id="target-type" value={targetType} onChange={(e) => setTargetType(e.target.value as "SCOPE" | "SUBJECT")}>
            <option value="SCOPE">SCOPE</option>
            <option value="SUBJECT">SUBJECT</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="target-id">Target ID</label>
          <input id="target-id" value={targetId} onChange={(e) => setTargetId(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="ent-key">Entitlement key</label>
          <select
            id="ent-key"
            value={entitlementKey}
            onChange={(e) => {
              setEntitlementKey(e.target.value);
              const next = resource.entitlementDefinitions.find((item) => item.key === e.target.value);
              setValue(defaultValue(next?.valueType ?? "BOOLEAN"));
            }}
          >
            {resource.entitlementDefinitions.map((item) => (
              <option key={item.key} value={item.key}>
                {item.key} ({item.valueType})
              </option>
            ))}
          </select>
        </div>
        <ValueFields value={value} valueType={definition?.valueType ?? "BOOLEAN"} onChange={setValue} />
        {error ? <div className="formError">{error}</div> : null}
        <div className="modalactions">
          <button className="btn" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" type="submit">
            Save
          </button>
        </div>
      </form>
    </Modal>
  );
}

function ValueFields({
  value,
  valueType,
  onChange,
}: {
  value: EntitlementValue;
  valueType: EntitlementValueType;
  onChange: (value: EntitlementValue) => void;
}) {
  if (valueType === "BOOLEAN" && value.type === "BOOLEAN") {
    return (
      <div className="field">
        <label htmlFor="bool">Allowed</label>
        <select id="bool" value={String(value.value)} onChange={(e) => onChange({ type: "BOOLEAN", value: e.target.value === "true" })}>
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
      </div>
    );
  }
  if (valueType === "QUANTITY" && value.type === "QUANTITY") {
    return (
      <>
        <NumberField label="Value" id="qty" value={value.value} onChange={(v) => onChange({ ...value, value: v })} />
        <TextField label="Unit" id="qty-unit" value={value.unit} onChange={(unit) => onChange({ ...value, unit })} />
      </>
    );
  }
  if (valueType === "QUOTA" && value.type === "QUOTA") {
    return (
      <>
        <NumberField label="Limit" id="quota" value={value.limit} onChange={(limit) => onChange({ ...value, limit })} />
        <TextField label="Unit" id="quota-unit" value={value.unit} onChange={(unit) => onChange({ ...value, unit })} />
        <div className="field">
          <label htmlFor="period">Period</label>
            <select id="period" value={value.period} onChange={(e) => onChange({ ...value, period: e.target.value as typeof value.period })}>
            <option>DAILY</option>
            <option>WEEKLY</option>
            <option>MONTHLY</option>
            <option>YEARLY</option>
          </select>
        </div>
      </>
    );
  }
  if (valueType === "RATE_LIMIT" && value.type === "RATE_LIMIT") {
    return (
      <>
        <NumberField label="Capacity" id="rl-cap" value={value.capacity} onChange={(capacity) => onChange({ ...value, capacity })} />
        <NumberField label="Refill tokens" id="rl-refill" value={value.refillTokens} onChange={(refillTokens) => onChange({ ...value, refillTokens })} />
        <TextField label="Refill period" id="rl-period" value={value.refillPeriod} onChange={(refillPeriod) => onChange({ ...value, refillPeriod })} />
      </>
    );
  }
  if (valueType === "RANGE" && value.type === "RANGE") {
    return (
      <>
        <NumberField label="Min" id="min" value={value.min} onChange={(min) => onChange({ ...value, min })} />
        <NumberField label="Max" id="max" value={value.max} onChange={(max) => onChange({ ...value, max })} />
        <TextField label="Unit" id="range-unit" value={value.unit} onChange={(unit) => onChange({ ...value, unit })} />
      </>
    );
  }
  if (valueType === "TIME_RANGE" && value.type === "TIME_RANGE") {
    return (
      <>
        <TextField label="From (ISO)" id="from" value={value.from} onChange={(from) => onChange({ ...value, from })} />
        <TextField label="Until (ISO)" id="until" value={value.until} onChange={(until) => onChange({ ...value, until })} />
      </>
    );
  }
  if (valueType === "SET" && value.type === "SET") {
    return (
      <TextField
        label="Values (comma separated)"
        id="set"
        value={value.values.join(", ")}
        onChange={(text) => onChange({ type: "SET", values: text.split(",").map((item) => item.trim()).filter(Boolean) })}
      />
    );
  }
  if (valueType === "TEXT" && value.type === "TEXT") {
    return <TextField label="Text" id="text" value={value.value} onChange={(text) => onChange({ type: "TEXT", value: text })} />;
  }
  return (
    <button className="btn small" type="button" onClick={() => onChange(defaultValue(valueType))}>
      Initialize {valueType} fields
    </button>
  );
}

function defaultValue(type: EntitlementValueType): EntitlementValue {
  switch (type) {
    case "BOOLEAN":
      return { type: "BOOLEAN", value: true };
    case "QUANTITY":
      return { type: "QUANTITY", value: 1, unit: "unit" };
    case "QUOTA":
      return { type: "QUOTA", limit: 100, unit: "unit", period: "MONTHLY" };
    case "RATE_LIMIT":
      return { type: "RATE_LIMIT", capacity: 100, refillTokens: 10, refillPeriod: "PT1M" };
    case "RANGE":
      return { type: "RANGE", min: 0, max: 1, unit: "value" };
    case "TIME_RANGE":
      return { type: "TIME_RANGE", from: new Date().toISOString(), until: new Date(Date.now() + 86400000).toISOString() };
    case "SET":
      return { type: "SET", values: [] };
    case "TEXT":
      return { type: "TEXT", value: "" };
  }
}

function typedValue(type: EntitlementValueType, value: EntitlementValue): EntitlementValue {
  return value.type === type ? value : defaultValue(type);
}

function NumberField({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: number | string;
  onChange: (value: number) => void;
}) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} type="number" value={value} onChange={(e) => onChange(Number(e.target.value))} required />
    </div>
  );
}

function TextField({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} value={value} onChange={(e) => onChange(e.target.value)} required />
    </div>
  );
}

function ConfirmRemove({
  message,
  onClose,
  onConfirm,
  onChanged,
}: {
  message: string;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal title="Confirm removal" onClose={onClose}>
      <p>{message}</p>
      {error ? <div className="formError">{error}</div> : null}
      <div className="modalactions">
        <button className="btn" type="button" onClick={onClose}>
          Cancel
        </button>
        <button
          className="btn danger"
          type="button"
          onClick={() => {
            void (async () => {
              try {
                await onConfirm();
                await onChanged();
                toast.show("Removed.");
                onClose();
              } catch (err) {
                setError(err instanceof Error ? err.message : "Request failed.");
              }
            })();
          }}
        >
          Confirm
        </button>
      </div>
    </Modal>
  );
}
