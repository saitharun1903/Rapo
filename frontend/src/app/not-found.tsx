import { Logo } from "@/components/layout/Logo";
import { ButtonLink } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="flex min-h-full flex-col items-center justify-center gap-4 px-4 text-center">
      <Logo />
      <h1 className="text-2xl font-semibold">This page does not exist</h1>
      <p className="text-fg-muted">The link may be old, or the address mistyped.</p>
      <ButtonLink href="/">Go home</ButtonLink>
    </main>
  );
}
