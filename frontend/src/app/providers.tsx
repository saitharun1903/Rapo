"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider, useTheme } from "next-themes";
import { useEffect, useState } from "react";
import { Toaster } from "sonner";
import { isApiError } from "@/lib/api/errors";
import { refreshSession } from "@/lib/api/client";
import { session } from "@/lib/auth/session";

const STALE_MS = 15_000;
const MAX_RETRIES = 2;
/** Retrying these cannot succeed: the request itself is wrong or not allowed. */
const CLIENT_ERROR_MIN = 400;
const CLIENT_ERROR_MAX = 499;

function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: STALE_MS,
        refetchOnWindowFocus: false,
        retry: (failures, error) => {
          const clientError = isApiError(error) && error.status >= CLIENT_ERROR_MIN && error.status <= CLIENT_ERROR_MAX;
          return !clientError && failures < MAX_RETRIES;
        },
      },
    },
  });
}

/**
 * Restores the session from the refresh cookie once per page load, and drops every cached response when the
 * session ends, so the next account signed in on this tab never sees the previous one's data.
 */
function SessionBootstrap({ queryClient }: { queryClient: QueryClient }) {
  useEffect(() => {
    if (session.get().status === "loading") {
      void refreshSession();
    }
    return session.subscribe(() => {
      if (session.get().status === "anonymous") {
        queryClient.clear();
      }
    });
  }, [queryClient]);
  return null;
}

function ThemedToaster() {
  const { resolvedTheme } = useTheme();
  return <Toaster position="top-center" richColors closeButton theme={resolvedTheme === "dark" ? "dark" : "light"} />;
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <QueryClientProvider client={queryClient}>
        <SessionBootstrap queryClient={queryClient} />
        {children}
        <ThemedToaster />
      </QueryClientProvider>
    </ThemeProvider>
  );
}
