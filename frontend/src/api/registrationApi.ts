import { apiRequest } from "./client";
import type { CompanyRegistrationRequest, RegistrationPreview } from "./types";

export async function previewRegistration(
  request: CompanyRegistrationRequest,
): Promise<RegistrationPreview> {
  return apiRequest<RegistrationPreview>("/api/company-registration/preview", {
    method: "POST",
    body: request,
  });
}
