import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { fetchTenant } from "../api/tenantApi";
import type { Tenant } from "../api/types";
import { useAuth } from "../auth/AuthContext";

interface TenantContextValue {
  tenant: Tenant | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

const TenantContext = createContext<TenantContextValue | null>(null);

export function TenantProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!user) {
      setTenant(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setTenant(await fetchTenant(user.tenantId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tenant");
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return (
    <TenantContext.Provider value={{ tenant, loading, error, reload }}>
      {children}
    </TenantContext.Provider>
  );
}

export function useTenant(): TenantContextValue {
  const context = useContext(TenantContext);
  if (!context) throw new Error("useTenant must be used within TenantProvider");
  return context;
}
