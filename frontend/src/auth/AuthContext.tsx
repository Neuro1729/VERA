import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { fetchCsrf, fetchMe, login as loginRequest, logout as logoutRequest } from "../api/authApi";
import { ApiClientError, setUnauthorizedHandler } from "../api/client";
import type { AuthMeResponse } from "../api/types";

export interface AuthUser {
  email: string;
  tenantId: string;
}

interface AuthState {
  ready: boolean;
  user: AuthUser | null;
  sessionMessage: string | null;
}

interface AuthContextValue extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  setUser: (user: AuthUser | null) => void;
  setSessionMessage: (message: string | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toUser(me: AuthMeResponse): AuthUser | null {
  if (!me.authenticated || !me.email || !me.tenantId) return null;
  return { email: me.email, tenantId: me.tenantId };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [sessionMessage, setSessionMessage] = useState<string | null>(null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      setSessionMessage("Your session expired. Sign in again.");
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await fetchCsrf();
        const me = await fetchMe();
        if (!cancelled) setUser(toUser(me));
      } catch (error) {
        if (!cancelled && error instanceof ApiClientError && error.status === 401) {
          setUser(null);
        }
      } finally {
        if (!cancelled) setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ready,
      user,
      sessionMessage,
      setUser,
      setSessionMessage,
      login: async (email, password) => {
        const me = await loginRequest({ email, password });
        const next = toUser(me);
        if (!next) throw new Error("Invalid email or password.");
        setUser(next);
        setSessionMessage(null);
      },
      logout: async () => {
        await logoutRequest();
        setUser(null);
        setSessionMessage(null);
      },
    }),
    [ready, user, sessionMessage],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
