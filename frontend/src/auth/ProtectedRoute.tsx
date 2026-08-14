import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";
import type { ReactNode } from "react";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { ready, user, sessionMessage } = useAuth();
  const location = useLocation();
  if (!ready) return <div className="loading">Loading VERA…</div>;
  if (!user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname, expired: Boolean(sessionMessage) }}
      />
    );
  }
  return children;
}

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { ready, user } = useAuth();
  if (!ready) return <div className="loading">Loading VERA…</div>;
  if (user) return <Navigate to="/workspace" replace />;
  return children;
}
