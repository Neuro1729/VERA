import { Link } from "react-router-dom";
import { GRANTS_TEMPLATE, ORGANIZATION_TEMPLATE, RESOURCES_TEMPLATE } from "../../api/templates";
import type { ScopeInput } from "../../api/types";
import { TopBar } from "../../components/layout/TopBar";
import { formatEntitlementValue } from "../../features/entitlement/formatValue";

export function DemoWorkspacePage() {
  return (
    <div className="app">
      <TopBar />
      <div className="workspaceShell">
        <aside className="sidebar">
          <div className="slabel">Sample</div>
          <nav className="nav">
            <span className="active" style={{ padding: "10px 11px" }}>
              Overview
            </span>
          </nav>
          <div className="tenantbox">
            <b>{ORGANIZATION_TEMPLATE.tenant.name}</b>
            <div>sample preview</div>
          </div>
        </aside>
        <main className="workcontent">
          <div className="workpage">
            <div className="banner warn">
              This is sample configuration from the JSON templates — not an authenticated workspace. Get started to create a real tenant.
            </div>
            <div className="workhead">
              <div>
                <div className="eyebrow">Sample preview</div>
                <h2>Demo workspace</h2>
                <p>Explore the organization / resources / grants shape without fabricating a session.</p>
              </div>
              <Link className="btn primary" to="/signup">
                Get started
              </Link>
            </div>
            <h3 className="paneltitle">Organization</h3>
            <SampleScope scope={ORGANIZATION_TEMPLATE.structure} />
            <h3 className="paneltitle" style={{ marginTop: 18 }}>
              Resources
            </h3>
            {RESOURCES_TEMPLATE.resources.map((resource) => (
              <div className="resourceCard" key={resource.id}>
                <div className="rowbtn">
                  <div>
                    <div className="rowtitle">{resource.name}</div>
                    <div className="rowsub">
                      {resource.kind} · {resource.entitlementDefinitions?.length ?? 0} definitions
                    </div>
                  </div>
                </div>
              </div>
            ))}
            <h3 className="paneltitle" style={{ marginTop: 18 }}>
              Grants
            </h3>
            {GRANTS_TEMPLATE.grants.map((grant) => (
              <div className="tinyrow" key={grant.id}>
                <span>{grant.id}</span>
                <span>
                  {grant.target.id} · {grant.entitlementKey} · {formatEntitlementValue(grant.value)}
                </span>
              </div>
            ))}
          </div>
        </main>
      </div>
    </div>
  );
}

function SampleScope({ scope }: { scope: ScopeInput }) {
  return (
    <div className="treegroup">
      <div className="rowbtn">
        <div>
          <div className="rowtitle">{scope.name}</div>
          <div className="rowsub">
            {scope.kind} · {scope.id}
          </div>
        </div>
      </div>
      <div className="expand open">
        {scope.subjects?.map((subject) => (
          <div className="tinyrow" key={subject.id}>
            <span>
              {subject.name} · {subject.id}
            </span>
          </div>
        ))}
        <div className="nested">
          {scope.children?.map((child) => (
            <SampleScope key={child.id} scope={child} />
          ))}
        </div>
      </div>
    </div>
  );
}
