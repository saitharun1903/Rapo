"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Segmented } from "@/components/ui/segmented";
import { Card, CardTitle, PageHeader } from "@/components/ui/surface";
import { api, logout, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { UserResponse } from "@/lib/api/types";
import { session } from "@/lib/auth/session";
import { useSession } from "@/lib/auth/useSession";
import { applyFieldErrors } from "@/lib/forms";
import { changePasswordSchema, profileSchema, type ChangePasswordForm, type ProfileForm } from "@/lib/validation";

const THEME_OPTIONS = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
  { value: "system", label: "System" },
] as const;
type ThemeChoice = (typeof THEME_OPTIONS)[number]["value"];

function ProfileCard({ user }: { user: UserResponse }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting, isDirty }, reset } = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user.fullName, phone: user.phone ?? "" },
  });
  const onSubmit = handleSubmit(async ({ fullName, phone }) => {
    try {
      const updated = await unwrap(api.PATCH("/api/users/me", { body: { fullName, phone: phone === "" ? null : phone } }));
      session.userUpdated(updated);
      reset({ fullName: updated.fullName, phone: updated.phone ?? "" });
      toast.success("Profile saved.");
    } catch (error) {
      if (isApiError(error, "PHONE_TAKEN")) {
        setError("phone", { message: "This phone number is already registered." });
      } else if (!applyFieldErrors(error, setError, ["fullName", "phone"])) {
        toast.error(errorMessage(error));
      }
    }
  });
  return (
    <Card>
      <CardTitle>Profile</CardTitle>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <p className="text-sm text-fg-muted">Email: <span className="font-medium text-fg">{user.email}</span></p>
        <Field label="Full name" error={errors.fullName?.message}>
          {({ id, describedBy, invalid }) => <Input id={id} autoComplete="name" aria-describedby={describedBy} aria-invalid={invalid} {...register("fullName")} />}
        </Field>
        <Field label="Phone (optional)" hint="International format, e.g. +919876543210" error={errors.phone?.message}>
          {({ id, describedBy, invalid }) => <Input id={id} type="tel" autoComplete="tel" aria-describedby={describedBy} aria-invalid={invalid} {...register("phone")} />}
        </Field>
        <Button type="submit" loading={isSubmitting} disabled={!isDirty} className="self-start">Save profile</Button>
      </form>
    </Card>
  );
}

function PasswordCard() {
  const router = useRouter();
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<ChangePasswordForm>({
    resolver: zodResolver(changePasswordSchema),
  });
  const onSubmit = handleSubmit(async (values) => {
    try {
      await unwrap(api.PUT("/api/users/me/password", { body: values }));
      toast.success("Password changed. Please sign in again.");
      await logout();
      router.replace("/login");
    } catch (error) {
      if (isApiError(error, "WRONG_CURRENT_PASSWORD")) {
        setError("currentPassword", { message: "That is not your current password." });
      } else if (!applyFieldErrors(error, setError, ["currentPassword", "newPassword"])) {
        toast.error(errorMessage(error));
      }
    }
  });
  return (
    <Card>
      <CardTitle>Password</CardTitle>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Field label="Current password" error={errors.currentPassword?.message}>
          {({ id, describedBy, invalid }) => <Input id={id} type="password" autoComplete="current-password" aria-describedby={describedBy} aria-invalid={invalid} {...register("currentPassword")} />}
        </Field>
        <Field label="New password" hint="At least 10 characters, with a letter and a digit" error={errors.newPassword?.message}>
          {({ id, describedBy, invalid }) => <Input id={id} type="password" autoComplete="new-password" aria-describedby={describedBy} aria-invalid={invalid} {...register("newPassword")} />}
        </Field>
        <p className="text-xs text-fg-muted">Changing your password signs you out on every device.</p>
        <Button type="submit" variant="secondary" loading={isSubmitting} className="self-start">Change password</Button>
      </form>
    </Card>
  );
}

export default function SettingsPage() {
  const current = useSession();
  const { theme, setTheme } = useTheme();
  if (current.status !== "authenticated") {
    return null;
  }
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-8">
      <PageHeader title="Settings" />
      <ProfileCard user={current.user} />
      <PasswordCard />
      <Card>
        <CardTitle>Appearance</CardTitle>
        <Segmented label="Theme" options={THEME_OPTIONS} value={(theme as ThemeChoice | undefined) ?? "system"} onChange={setTheme} />
      </Card>
    </div>
  );
}
