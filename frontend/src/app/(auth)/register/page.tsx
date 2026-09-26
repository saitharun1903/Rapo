"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import clsx from "clsx";
import { Car, User } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { register as registerAccount } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import { applyFieldErrors } from "@/lib/forms";
import { registerSchema, type RegisterForm } from "@/lib/validation";

const ACCOUNT_TYPES = [
  { value: "PASSENGER", label: "I want to ride", icon: User },
  { value: "DRIVER", label: "I want to drive", icon: Car },
] as const;

function RegisterFormView() {
  const router = useRouter();
  const asDriver = useSearchParams().get("as") === "driver";
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, control, setValue, setError, formState: { errors, isSubmitting } } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { accountType: asDriver ? "DRIVER" : "PASSENGER", phone: "" },
  });
  const accountType = useWatch({ control, name: "accountType" });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const auth = await registerAccount({ ...values, phone: values.phone === "" ? null : values.phone });
      // New drivers submit their licence and vehicle before they can go online.
      router.replace(auth.user.role === "DRIVER" ? "/drive/onboarding" : "/ride");
    } catch (error) {
      if (isApiError(error, "EMAIL_TAKEN")) {
        setError("email", { message: "An account with this email already exists." });
      } else if (isApiError(error, "PHONE_TAKEN")) {
        setError("phone", { message: "This phone number is already registered." });
      } else if (!applyFieldErrors(error, setError, ["fullName", "email", "phone", "password", "accountType"])) {
        setFormError(errorMessage(error));
      }
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div>
        <h1 className="text-[1.75rem] font-semibold leading-tight tracking-[-0.03em]">Create your account</h1>
        <p className="mt-1 text-sm text-fg-muted">It takes a minute.</p>
      </div>
      {formError && <p role="alert" className="rounded-control bg-danger-soft px-3 py-2 text-sm font-medium text-danger">{formError}</p>}
      <div role="radiogroup" aria-label="Account type" className="grid grid-cols-2 gap-2">
        {ACCOUNT_TYPES.map(({ value, label, icon: Icon }) => (
          <button key={value} type="button" role="radio" aria-checked={accountType === value}
            onClick={() => setValue("accountType", value)}
            className={clsx("flex flex-col items-center gap-1 rounded-control border px-3 py-3 text-sm font-semibold",
              accountType === value ? "border-brand bg-brand-soft text-brand-strong" : "border-line text-fg-muted hover:bg-surface-2")}>
            <Icon className="size-5" aria-hidden />
            {label}
          </button>
        ))}
      </div>
      <Field label="Full name" error={errors.fullName?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} autoComplete="name" aria-describedby={describedBy} aria-invalid={invalid} {...register("fullName")} />
        )}
      </Field>
      <Field label="Email" error={errors.email?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="email" autoComplete="email" aria-describedby={describedBy} aria-invalid={invalid} {...register("email")} />
        )}
      </Field>
      <Field label="Phone (optional)" hint="International format, e.g. +919876543210" error={errors.phone?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="tel" autoComplete="tel" aria-describedby={describedBy} aria-invalid={invalid} {...register("phone")} />
        )}
      </Field>
      <Field label="Password" hint="At least 10 characters, with a letter and a digit" error={errors.password?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="password" autoComplete="new-password" aria-describedby={describedBy} aria-invalid={invalid}
            {...register("password")} />
        )}
      </Field>
      <Button type="submit" size="lg" loading={isSubmitting}>Create account</Button>
      <p className="text-center text-sm text-fg-muted">
        Already have an account? <Link href="/login" className="font-semibold text-brand-strong">Sign in</Link>
      </p>
    </form>
  );
}

export default function RegisterPage() {
  return <Suspense><RegisterFormView /></Suspense>;
}
