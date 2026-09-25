import Link from "next/link";
import { Logo } from "@/components/layout/Logo";
import { config } from "@/lib/config";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-8 px-4 py-12">
      <Link href="/" aria-label="RideFlow home"><Logo /></Link>
      <main className="w-full max-w-md rounded-2xl border border-line bg-surface p-6 shadow-sm sm:p-8">
        {config.backendConfigured ? children : (
          <div role="status" className="flex flex-col gap-2">
            <h1 className="text-xl font-bold tracking-tight">Not available yet</h1>
            <p className="text-sm text-fg-muted">
              This deployment has no backend configured yet, so signing in and creating accounts are unavailable.
            </p>
          </div>
        )}
      </main>
    </div>
  );
}
