import { apiRequest } from "./client";
import { fetchCsrf } from "./authApi";
import type { CommandRequest, CommandResult } from "./types";

export async function executeCommand(request: CommandRequest): Promise<CommandResult> {
  await fetchCsrf();
  return apiRequest<CommandResult>("/api/commands", {
    method: "POST",
    body: request,
    authRequired: true,
  });
}
