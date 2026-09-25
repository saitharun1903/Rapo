"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Textarea } from "@/components/ui/form";
import { reasonSchema } from "@/lib/validation";

type ReasonDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  onConfirm: (reason: string) => void;
  onClose: () => void;
};

/** Every consequential admin action records a reason in the audit log. */
export function ReasonDialog({ open, title, description, confirmLabel, pending, onConfirm, onClose }: ReasonDialogProps) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<{ reason: string }>({
    resolver: zodResolver(reasonSchema),
    defaultValues: { reason: "" },
  });
  useEffect(() => {
    if (open) {
      reset();
    }
  }, [open, reset]);
  return (
    <Dialog open={open} onClose={onClose} title={title}>
      <form onSubmit={handleSubmit(({ reason }) => onConfirm(reason))} noValidate className="flex flex-col gap-4">
        <p className="text-sm text-fg-muted">{description}</p>
        <Field label="Reason (recorded in the audit log)" error={errors.reason?.message}>
          {({ id, describedBy, invalid }) => <Textarea id={id} aria-describedby={describedBy} aria-invalid={invalid} {...register("reason")} />}
        </Field>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="danger" loading={pending}>{confirmLabel}</Button>
        </div>
      </form>
    </Dialog>
  );
}
