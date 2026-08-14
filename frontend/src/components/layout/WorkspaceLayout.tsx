import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { TenantProvider, useTenant } from "../../auth/TenantContext";
import { TopBar } from "./TopBar";

const LINKS = [
  { to: "/workspace", label: "Overview", end: true },
  { to: "/workspace/organization", label: "Organization" },
  { to: "/workspace/resources", label: "Resources" },
  { to: "/workspace/integration", label: "Integration" },
  { to: "/workspace/entitlement-history", label: "Entitlement History" },
  { to: "/workspace/usage-history", label: "Usage History" },
];

function Shell() {
  const { user } = useAuth();
  const { tenant } = useTenant();
  return (
    <div className="app">
      <TopBar />
      <div className="workspaceShell">
        <aside className="sidebar">
          <div className="slabel">Workspace</div>
          <nav className="nav" aria-label="Workspace">
            {LINKS.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end} className={({ isActive }) => (isActive ? "active" : "")}>
                <span className="txt">{link.label}</span>
              </NavLink>
            ))}
          </nav>
          <div className="tenantbox">
            <b>{tenant?.name ?? "…"}</b>
            <div>tenant: {user?.tenantId}</div>
            <div style={{ color: "var(--green)", marginTop: 7 }}>● admin session active</div>
          </div>
        </aside>
        <main className="workcontent">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function WorkspaceLayout() {
  return (
    <TenantProvider>
      <Shell />
    </TenantProvider>
  );
}
