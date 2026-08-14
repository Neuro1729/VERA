import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { useEffect, useState } from "react";
import { SignupDraftProvider, useSignupDraft } from "../../auth/SignupDraftContext";
import { parseJsonObject, readJsonFile } from "../../components/forms/JsonConfigField";
import { SignupPage } from "./SignupPage";
import { ValidationPage } from "./ValidationPage";
import { ApiKeyRevealPage } from "./ApiKeyRevealPage";
import { AuthProvider } from "../../auth/AuthContext";

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as Response;
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/preview")) {
        return jsonResponse({
          valid: true,
          summary: {
            scopeCount: 2,
            subjectCount: 1,
            resourceCount: 1,
            entitlementDefinitionCount: 2,
            grantCount: 1,
            invalidGrantCount: 0,
            errorCount: 0,
            warningCount: 0,
          },
          issues: [],
        });
      }
      if (String(url).includes("/signup")) {
        return jsonResponse({ tenantId: "acme", admin: { email: "ops@example.com" }, apiKey: "vera_live_once.secret" }, 201);
      }
      return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
    }),
  );
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

test("admin credentials move to configuration without persistence", async () => {
  function Dump() {
    const { draft } = useSignupDraft();
    return <div>draft-email:{draft.email}</div>;
  }
  render(
    <AuthProvider>
      <SignupDraftProvider>
        <MemoryRouter initialEntries={["/signup"]}>
          <Routes>
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/signup/configuration" element={<Dump />} />
          </Routes>
        </MemoryRouter>
      </SignupDraftProvider>
    </AuthProvider>,
  );
  await userEvent.type(screen.getByLabelText("Work email"), "ops@example.com");
  await userEvent.type(screen.getByLabelText("Password"), "a-long-password");
  await userEvent.click(screen.getByRole("button", { name: "Continue to configuration" }));
  expect(await screen.findByText("draft-email:ops@example.com")).toBeInTheDocument();
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

test("JSON file parsing works", async () => {
  const file = new File([JSON.stringify({ hello: "world" })], "x.json", { type: "application/json" });
  await expect(readJsonFile(file)).resolves.toContain("hello");
});

test("invalid JSON is rejected", () => {
  expect(parseJsonObject("{nope")).toEqual({ ok: false, error: "Invalid JSON." });
});

test("registration preview response is rendered", async () => {
  function Seed() {
    const { setCredentials, setOrganizationJson, setResourcesJson, setGrantsJson } = useSignupDraft();
    const [ready, setReady] = useState(false);
    useEffect(() => {
      setCredentials("ops@example.com", "a-long-password");
      setOrganizationJson(JSON.stringify({ tenant: { id: "acme", name: "Acme" }, structure: { id: "root", kind: "company", name: "Acme" } }));
      setResourcesJson(JSON.stringify({ resources: [] }));
      setGrantsJson(JSON.stringify({ grants: [] }));
      setReady(true);
    }, [setCredentials, setGrantsJson, setOrganizationJson, setResourcesJson]);
    return ready ? <ValidationPage /> : null;
  }
  render(
    <AuthProvider>
      <SignupDraftProvider>
        <MemoryRouter>
          <Seed />
        </MemoryRouter>
      </SignupDraftProvider>
    </AuthProvider>,
  );
  expect(await screen.findByText(/2 scopes/)).toBeInTheDocument();
  expect(screen.getByText(/1 subjects/)).toBeInTheDocument();
});

test("final signup renders raw API key once and does not store it", async () => {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[{ pathname: "/signup/api-key", state: { apiKey: "vera_live_once.secret", tenantId: "acme" } }]}>
        <Routes>
          <Route path="/signup/api-key" element={<ApiKeyRevealPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  expect(await screen.findByText("vera_live_once.secret")).toBeInTheDocument();
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

test("refresh/no route state cannot redisplay old raw key", async () => {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/signup/api-key"]}>
        <Routes>
          <Route path="/signup/api-key" element={<ApiKeyRevealPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  expect(await screen.findByText(/can no longer be displayed/i)).toBeInTheDocument();
  expect(screen.queryByText("vera_live_once.secret")).not.toBeInTheDocument();
});

test("final signup navigates to one-time API key page after draft is cleared", async () => {
  function Seed() {
    const { setCredentials, setOrganizationJson, setResourcesJson, setGrantsJson } = useSignupDraft();
    const [ready, setReady] = useState(false);
    useEffect(() => {
      setCredentials("ops@example.com", "a-long-password");
      setOrganizationJson(
        JSON.stringify({ tenant: { id: "acme", name: "Acme" }, structure: { id: "root", kind: "company", name: "Acme" } }),
      );
      setResourcesJson(JSON.stringify({ resources: [] }));
      setGrantsJson(JSON.stringify({ grants: [] }));
      setReady(true);
    }, [setCredentials, setGrantsJson, setOrganizationJson, setResourcesJson]);
    return ready ? <ValidationPage /> : null;
  }
  render(
    <AuthProvider>
      <SignupDraftProvider>
        <MemoryRouter initialEntries={["/signup/validation"]}>
          <Routes>
            <Route path="/signup/validation" element={<Seed />} />
            <Route path="/signup" element={<div>signup-restarted</div>} />
            <Route path="/workspace" element={<div>workspace-skip</div>} />
            <Route path="/signup/api-key" element={<ApiKeyRevealPage />} />
          </Routes>
        </MemoryRouter>
      </SignupDraftProvider>
    </AuthProvider>,
  );
  await userEvent.click(await screen.findByRole("button", { name: "Create workspace & API key" }));
  expect(await screen.findByText("vera_live_once.secret")).toBeInTheDocument();
  expect(screen.queryByText("signup-restarted")).not.toBeInTheDocument();
  expect(screen.queryByText("workspace-skip")).not.toBeInTheDocument();
});
