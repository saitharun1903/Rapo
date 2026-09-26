"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { login } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import { homeFor } from "@/lib/auth/useSession";
import { safeNextPath } from "@/lib/forms";
import { loginSchema, type LoginForm } from "@/lib/validation";

function LoginForm() {
  const router = useRouter();
  const next = safeNextPath(useSearchParams().get("next"));
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null);
    try {
      const auth = await login(email, password);
      router.replace(next ?? homeFor(auth.user.role));
    } catch (error) {
      setFormError(isApiError(error, "INVALID_CREDENTIALS") ? "That email and password do not match." : errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div>
        <h1 className="text-[1.75rem] font-semibold leading-tight tracking-[-0.03em]">Welcome back</h1>
        <p className="mt-1 text-sm text-fg-muted">Sign in to book or drive.</p>
      </div>
      {formError && <p role="alert" className="rounded-control bg-danger-soft px-3 py-2 text-sm font-medium text-danger">{formError}</p>}
      <Field label="Email" error={errors.email?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="email" autoComplete="email" aria-describedby={describedBy} aria-invalid={invalid} {...register("email")} />
        )}
      </Field>
      <Field label="Password" error={errors.password?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="password" autoComplete="current-password" aria-describedby={describedBy} aria-invalid={invalid}
            {...register("password")} />
        )}
      </Field>
      <Button type="submit" size="lg" loading={isSubmitting}>Sign in</Button>
      <p className="text-center text-sm text-fg-muted">
        New to Raido? <Link href="/register" className="font-semibold text-brand-strong">Create an account</Link>
      </p>
    </form>
  );
}

export default function LoginPage() {
  // useSearchParams needs a Suspense boundary so the rest of the page can be prerendered.
  return <Suspense><LoginForm /></Suspense>;
}
