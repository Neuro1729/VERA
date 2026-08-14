import { createBrowserRouter, Navigate } from "react-router-dom";
import { ProtectedRoute, PublicOnlyRoute } from "../auth/ProtectedRoute";
import { WorkspaceLayout } from "../components/layout/WorkspaceLayout";
import { DemoWorkspacePage } from "../pages/demo/DemoWorkspacePage";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { ApiKeyRevealPage } from "../pages/signup/ApiKeyRevealPage";
import { OnboardingPage } from "../pages/signup/OnboardingPage";
import { SignupPage } from "../pages/signup/SignupPage";
import { ValidationPage } from "../pages/signup/ValidationPage";
import { EntitlementHistoryPage } from "../pages/workspace/EntitlementHistoryPage";
import { IntegrationPage } from "../pages/workspace/IntegrationPage";
import { OrganizationPage } from "../pages/workspace/OrganizationPage";
import { OverviewPage } from "../pages/workspace/OverviewPage";
import { ResourcesPage } from "../pages/workspace/ResourcesPage";
import { UsageHistoryPage } from "../pages/workspace/UsageHistoryPage";

export const router = createBrowserRouter([
  { path: "/", element: <LandingPage /> },
  { path: "/demo", element: <DemoWorkspacePage /> },
  {
    path: "/login",
    element: (
      <PublicOnlyRoute>
        <LoginPage />
      </PublicOnlyRoute>
    ),
  },
  {
    path: "/signup",
    element: (
      <PublicOnlyRoute>
        <SignupPage />
      </PublicOnlyRoute>
    ),
  },
  { path: "/signup/configuration", element: <OnboardingPage /> },
  { path: "/signup/validation", element: <ValidationPage /> },
  { path: "/signup/api-key", element: <ApiKeyRevealPage /> },
  {
    path: "/workspace",
    element: (
      <ProtectedRoute>
        <WorkspaceLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <OverviewPage /> },
      { path: "organization", element: <OrganizationPage /> },
      { path: "resources", element: <ResourcesPage /> },
      { path: "integration", element: <IntegrationPage /> },
      { path: "entitlement-history", element: <EntitlementHistoryPage /> },
      { path: "usage-history", element: <UsageHistoryPage /> },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
