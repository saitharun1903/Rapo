/**
 * A minimal client for the RideFlow REST API, signed in as one seeded account. It uses the same public
 * endpoints as the web app; there is no simulator backdoor. On 401 (the 15-minute access token expired) it
 * signs in again once and repeats the request.
 */

const UNAUTHORIZED = 401;
const NO_CONTENT = 204;

export class ApiCallError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

type Method = "GET" | "POST" | "PUT";

export class ApiClient {
  private token: string | null = null;
  private readonly baseUrl: string;
  readonly email: string;
  private readonly password: string;

  constructor(baseUrl: string, email: string, password: string) {
    this.baseUrl = baseUrl;
    this.email = email;
    this.password = password;
  }

  async signIn(): Promise<void> {
    const response = await fetch(`${this.baseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: this.email, password: this.password }),
    });
    if (!response.ok) {
      throw await ApiClient.failure(response);
    }
    this.token = ((await response.json()) as { accessToken: string }).accessToken;
  }

  get accessToken(): string {
    if (this.token === null) {
      throw new Error(`${this.email} is not signed in`);
    }
    return this.token;
  }

  /** The parsed body, or null for 204. Throws ApiCallError for any other non-2xx answer. */
  async call<T>(method: Method, path: string, body?: unknown): Promise<T | null> {
    let response = await this.send(method, path, body);
    if (response.status === UNAUTHORIZED) {
      await this.signIn();
      response = await this.send(method, path, body);
    }
    if (!response.ok) {
      throw await ApiClient.failure(response);
    }
    const hasBody = response.status !== NO_CONTENT && response.headers.get("Content-Type")?.includes("json");
    return hasBody ? (await response.json()) as T : null;
  }

  async get<T>(path: string): Promise<T> {
    return this.required(await this.call<T>("GET", path), path);
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.required(await this.call<T>("POST", path, body), path);
  }

  private required<T>(value: T | null, path: string): T {
    if (value === null) {
      throw new Error(`Expected a body from ${path}`);
    }
    return value;
  }

  private send(method: Method, path: string, body?: unknown): Promise<Response> {
    const headers: Record<string, string> = { Authorization: `Bearer ${this.accessToken}` };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    return fetch(`${this.baseUrl}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  }

  private static async failure(response: Response): Promise<ApiCallError> {
    try {
      const error = (await response.json()) as { code?: string; message?: string };
      return new ApiCallError(response.status, error.code ?? "UNKNOWN", error.message ?? response.statusText);
    } catch {
      return new ApiCallError(response.status, "UNKNOWN", `HTTP ${response.status}`);
    }
  }
}

export function isCode(error: unknown, ...codes: string[]): boolean {
  return error instanceof ApiCallError && codes.includes(error.code);
}
