import type { Mock } from "vitest";
import { json } from "./fixtures";

const SUCCESS_MAX = 299;

/**
 * Makes a mocked openapi-fetch method (api.GET, api.POST) answer with `body` and `status`, shaped the way
 * openapi-fetch resolves: `data` on success, `error` otherwise, and the raw response.
 */
export function answer(method: Mock, body: unknown, status = 200): void {
  const ok = status <= SUCCESS_MAX;
  method.mockImplementation(() => Promise.resolve({
    data: ok ? body : undefined,
    error: ok ? undefined : body,
    response: json(body, status),
  }));
}
