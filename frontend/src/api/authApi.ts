import { apiRequest, setCsrfToken } from "./client";
import type {
  ApiKeyMetadataResponse,
  ApiKeyRotationResponse,
  AuthMeResponse,
  CompanySignupRequest,
  CompanySignupResponse,
  CsrfTokenResponse,
  LoginRequest,
} from "./types";

export async function fetchCsrf(): Promise<CsrfTokenResponse> {
  const csrf = await apiRequest<CsrfTokenResponse>("/api/auth/csrf");
  setCsrfToken(csrf.token, csrf.headerName);
  return csrf;
}

export async function fetchMe(): Promise<AuthMeResponse> {
  return apiRequest<AuthMeResponse>("/api/auth/me");
}

export async function login(request: LoginRequest): Promise<AuthMeResponse> {
  await fetchCsrf();
  return apiRequest<AuthMeResponse>("/api/auth/login", { method: "POST", body: request });
}

export async function signup(request: CompanySignupRequest): Promise<CompanySignupResponse> {
  await fetchCsrf();
  return apiRequest<CompanySignupResponse>("/api/auth/signup", { method: "POST", body: request });
}

export async function logout(): Promise<AuthMeResponse> {
  await fetchCsrf();
  return apiRequest<AuthMeResponse>("/api/auth/logout", { method: "POST" });
}

export async function fetchApiKeyMetadata(): Promise<ApiKeyMetadataResponse> {
  return apiRequest<ApiKeyMetadataResponse>("/api/auth/api-key", { authRequired: true });
}

export async function rotateApiKey(): Promise<ApiKeyRotationResponse> {
  await fetchCsrf();
  return apiRequest<ApiKeyRotationResponse>("/api/auth/api-key/rotate", {
    method: "POST",
    authRequired: true,
  });
}
