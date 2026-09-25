/** One line per event, prefixed with the time and the actor. Never given passwords or tokens. */
export function log(actor: string, message: string): void {
  console.log(`${new Date().toISOString()} [${actor}] ${message}`);
}

export function logError(actor: string, message: string, error: unknown): void {
  const detail = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  console.error(`${new Date().toISOString()} [${actor}] ${message}: ${detail}`);
}

export const sleep = (ms: number, signal?: AbortSignal) => new Promise<void>((resolve, reject) => {
  if (signal?.aborted) {
    reject(signal.reason);
    return;
  }
  const timer = setTimeout(resolve, ms);
  signal?.addEventListener("abort", () => {
    clearTimeout(timer);
    reject(signal.reason);
  }, { once: true });
});
