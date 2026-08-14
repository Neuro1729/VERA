import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

interface SignupDraft {
  email: string;
  password: string;
  organizationJson: string;
  resourcesJson: string;
  grantsJson: string;
}

interface SignupDraftContextValue {
  draft: SignupDraft;
  setCredentials: (email: string, password: string) => void;
  setOrganizationJson: (value: string) => void;
  setResourcesJson: (value: string) => void;
  setGrantsJson: (value: string) => void;
  clearDraft: () => void;
  hasCredentials: boolean;
}

const empty: SignupDraft = {
  email: "",
  password: "",
  organizationJson: "",
  resourcesJson: "",
  grantsJson: "",
};

const SignupDraftContext = createContext<SignupDraftContextValue | null>(null);

export function SignupDraftProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<SignupDraft>(empty);
  const setCredentials = useCallback((email: string, password: string) => {
    setDraft((current) => ({ ...current, email, password }));
  }, []);
  const setOrganizationJson = useCallback((organizationJson: string) => {
    setDraft((current) => ({ ...current, organizationJson }));
  }, []);
  const setResourcesJson = useCallback((resourcesJson: string) => {
    setDraft((current) => ({ ...current, resourcesJson }));
  }, []);
  const setGrantsJson = useCallback((grantsJson: string) => {
    setDraft((current) => ({ ...current, grantsJson }));
  }, []);
  const clearDraft = useCallback(() => setDraft(empty), []);
  const value = useMemo<SignupDraftContextValue>(
    () => ({
      draft,
      hasCredentials: draft.email.length > 0 && draft.password.length >= 12,
      setCredentials,
      setOrganizationJson,
      setResourcesJson,
      setGrantsJson,
      clearDraft,
    }),
    [clearDraft, draft, setCredentials, setGrantsJson, setOrganizationJson, setResourcesJson],
  );
  return <SignupDraftContext.Provider value={value}>{children}</SignupDraftContext.Provider>;
}

export function useSignupDraft(): SignupDraftContextValue {
  const context = useContext(SignupDraftContext);
  if (!context) throw new Error("useSignupDraft must be used within SignupDraftProvider");
  return context;
}
