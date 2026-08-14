import { useState } from "react";
import { executeCommand } from "../../api/commandApi";
import type { Scope, Subject, Tenant } from "../../api/types";
import { Modal } from "../../components/common/Modal";
import { useToast } from "../../components/common/Toast";
import { countDescendantSubjects } from "../entitlement/formatValue";

interface Props {
  tenant: Tenant;
  onChanged: () => Promise<void>;
}

export function ScopeTree({ tenant, onChanged }: Props) {
  const root = tenant.scopes[tenant.rootScopeId];
  if (!root) return <div className="empty">No organization structure yet.</div>;
  return <ScopeNode tenant={tenant} scope={root} onChanged={onChanged} />;
}

function ScopeNode({ tenant, scope, onChanged }: { tenant: Tenant; scope: Scope; onChanged: () => Promise<void> }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<null | "addChild" | "edit" | "move" | "addSubject" | "remove">(null);
  const subjects = scope.subjectIds.map((id) => tenant.subjects[id]).filter(Boolean);
  const count = countDescendantSubjects(tenant, scope.id);

  return (
    <div className="treegroup">
      <button className="rowbtn" type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <div className="leftlabel">
          <div className="chev">{open ? "▾" : "›"}</div>
          <div>
            <div className="rowtitle">{scope.name}</div>
            <div className="rowsub">
              {scope.kind} · {scope.id} · {count} subjects
            </div>
          </div>
        </div>
        <span className="rowsub">{open ? "collapse" : "expand"}</span>
      </button>
      <div className={`expand ${open ? "open" : ""}`}>
        <div className="inlineActions">
          <button className="btn small soft" type="button" onClick={() => setMode("addChild")}>
            Add child scope
          </button>
          <button className="btn small" type="button" onClick={() => setMode("edit")}>
            Edit scope
          </button>
          {scope.parentScopeId ? (
            <button className="btn small" type="button" onClick={() => setMode("move")}>
              Move
            </button>
          ) : null}
          <button className="btn small" type="button" onClick={() => setMode("addSubject")}>
            Add subject
          </button>
          {scope.parentScopeId ? (
            <button className="btn small danger" type="button" onClick={() => setMode("remove")}>
              Remove
            </button>
          ) : null}
        </div>
        {subjects.map((subject) => (
          <SubjectRow key={subject.id} tenant={tenant} subject={subject} onChanged={onChanged} />
        ))}
        <div className="nested">
          {scope.childScopeIds.map((childId) => {
            const child = tenant.scopes[childId];
            return child ? <ScopeNode key={child.id} tenant={tenant} scope={child} onChanged={onChanged} /> : null;
          })}
        </div>
      </div>
      {mode === "addChild" ? (
        <ScopeForm
          title="Add child scope"
          onClose={() => setMode(null)}
          onSubmit={async (values) => {
            await executeCommand({
              type: "ADD_SCOPE",
              tenantId: tenant.id,
              payload: { parentScopeId: scope.id, scope: values },
            });
          }}
          onChanged={onChanged}
        />
      ) : null}
      {mode === "edit" ? (
        <ScopeForm
          title="Edit scope"
          initial={scope}
          onClose={() => setMode(null)}
          onSubmit={async (values) => {
            await executeCommand({
              type: "UPDATE_SCOPE",
              tenantId: tenant.id,
              payload: { scopeId: scope.id, kind: values.kind, name: values.name, metadata: values.metadata, replaceMetadata: true },
            });
          }}
          onChanged={onChanged}
        />
      ) : null}
      {mode === "move" ? (
        <IdForm
          title="Move scope"
          label="New parent scope ID"
          onClose={() => setMode(null)}
          onSubmit={async (newParentScopeId) => {
            await executeCommand({
              type: "MOVE_SCOPE",
              tenantId: tenant.id,
              payload: { scopeId: scope.id, newParentScopeId },
            });
          }}
          onChanged={onChanged}
        />
      ) : null}
      {mode === "addSubject" ? (
        <SubjectForm
          title="Add subject"
          onClose={() => setMode(null)}
          onSubmit={async (values) => {
            await executeCommand({
              type: "ADD_SUBJECT",
              tenantId: tenant.id,
              payload: { scopeId: scope.id, subject: values },
            });
          }}
          onChanged={onChanged}
        />
      ) : null}
      {mode === "remove" ? (
        <ConfirmForm
          title="Remove scope"
          message="This removes the scope and cascades related grants. Continue?"
          onClose={() => setMode(null)}
          onConfirm={async () => {
            await executeCommand({ type: "REMOVE_SCOPE", tenantId: tenant.id, payload: { scopeId: scope.id } });
          }}
          onChanged={onChanged}
        />
      ) : null}
    </div>
  );
}

function SubjectRow({ tenant, subject, onChanged }: { tenant: Tenant; subject: Subject; onChanged: () => Promise<void> }) {
  const [mode, setMode] = useState<null | "edit" | "move" | "remove">(null);
  return (
    <div className="tinyrow">
      <span>
        {subject.name} · {subject.id}
      </span>
      <span>
        <button className="btn small" type="button" onClick={() => setMode("edit")}>
          Edit
        </button>{" "}
        <button className="btn small" type="button" onClick={() => setMode("move")}>
          Move
        </button>{" "}
        <button className="btn small danger" type="button" onClick={() => setMode("remove")}>
          Remove
        </button>
      </span>
      {mode === "edit" ? (
        <SubjectForm
          title="Edit subject"
          initial={subject}
          onClose={() => setMode(null)}
          onSubmit={async (values) => {
            await executeCommand({
              type: "UPDATE_SUBJECT",
              tenantId: tenant.id,
              payload: { subjectId: subject.id, kind: values.kind, name: values.name, metadata: values.metadata, replaceMetadata: true },
            });
          }}
          onChanged={onChanged}
        />
      ) : null}
      {mode === "move" ? (
        <IdForm
          title="Move subject"
          label="New scope ID"
          onClose={() => setMode(null)}
          onSubmit={async (newScopeId) => {
            await executeCommand({
              type: "MOVE_SUBJECT",
              tenantId: tenant.id,
              payload: { subjectId: subject.id, newScopeId },
            });
          }}
          onChanged={onChanged}
        />
      ) : null}
      {mode === "remove" ? (
        <ConfirmForm
          title="Remove subject"
          message={`Remove ${subject.name}? This cannot be undone.`}
          onClose={() => setMode(null)}
          onConfirm={async () => {
            await executeCommand({ type: "REMOVE_SUBJECT", tenantId: tenant.id, payload: { subjectId: subject.id } });
          }}
          onChanged={onChanged}
        />
      ) : null}
    </div>
  );
}

function ScopeForm({
  title,
  initial,
  onClose,
  onSubmit,
  onChanged,
}: {
  title: string;
  initial?: Scope;
  onClose: () => void;
  onSubmit: (values: { id: string; kind: string; name: string; metadata: Record<string, unknown> }) => Promise<void>;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [id, setId] = useState(initial?.id ?? "");
  const [kind, setKind] = useState(initial?.kind ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal title={title} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void (async () => {
            try {
              await onSubmit({ id, kind, name, metadata: initial?.metadata ?? {} });
              await onChanged();
              toast.show("Scope updated.");
              onClose();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Request failed.");
            }
          })();
        }}
      >
        {!initial ? (
          <div className="field">
            <label htmlFor="scope-id">ID</label>
            <input id="scope-id" value={id} onChange={(e) => setId(e.target.value)} required />
          </div>
        ) : null}
        <div className="field">
          <label htmlFor="scope-kind">Kind</label>
          <input id="scope-kind" value={kind} onChange={(e) => setKind(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="scope-name">Name</label>
          <input id="scope-name" value={name} onChange={(e) => setName(e.target.value)} required />
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

function SubjectForm({
  title,
  initial,
  onClose,
  onSubmit,
  onChanged,
}: {
  title: string;
  initial?: Subject;
  onClose: () => void;
  onSubmit: (values: { id: string; kind: string; name: string; metadata?: Record<string, unknown> }) => Promise<void>;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [id, setId] = useState(initial?.id ?? "");
  const [kind, setKind] = useState(initial?.kind ?? "employee");
  const [name, setName] = useState(initial?.name ?? "");
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal title={title} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void (async () => {
            try {
              await onSubmit({ id, kind, name, metadata: initial?.metadata ?? {} });
              await onChanged();
              toast.show("Subject updated.");
              onClose();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Request failed.");
            }
          })();
        }}
      >
        {!initial ? (
          <div className="field">
            <label htmlFor="subject-id">ID</label>
            <input id="subject-id" value={id} onChange={(e) => setId(e.target.value)} required />
          </div>
        ) : null}
        <div className="field">
          <label htmlFor="subject-kind">Kind</label>
          <input id="subject-kind" value={kind} onChange={(e) => setKind(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="subject-name">Name</label>
          <input id="subject-name" value={name} onChange={(e) => setName(e.target.value)} required />
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

function IdForm({
  title,
  label,
  onClose,
  onSubmit,
  onChanged,
}: {
  title: string;
  label: string;
  onClose: () => void;
  onSubmit: (value: string) => Promise<void>;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal title={title} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void (async () => {
            try {
              await onSubmit(value);
              await onChanged();
              toast.show("Updated.");
              onClose();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Request failed.");
            }
          })();
        }}
      >
        <div className="field">
          <label htmlFor="move-id">{label}</label>
          <input id="move-id" value={value} onChange={(e) => setValue(e.target.value)} required />
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

function ConfirmForm({
  title,
  message,
  onClose,
  onConfirm,
  onChanged,
}: {
  title: string;
  message: string;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal title={title} onClose={onClose}>
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
