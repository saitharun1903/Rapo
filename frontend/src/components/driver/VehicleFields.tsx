"use client";

import type { FieldErrors, UseFormRegisterReturn } from "react-hook-form";
import { Field, Input, Select } from "@/components/ui/form";
import type { VehicleForm } from "@/lib/validation";

const CATEGORIES = [
  { value: "ECONOMY", label: "Economy" },
  { value: "COMFORT", label: "Comfort" },
  { value: "XL", label: "XL (6+ seats)" },
] as const;

type Props = {
  /** Registers one vehicle field with the parent form, wherever the vehicle sits in it. */
  field: (name: keyof VehicleForm) => UseFormRegisterReturn;
  errors: FieldErrors<VehicleForm> | undefined;
};

/** The vehicle part of onboarding and of a vehicle replacement. */
export function VehicleFields({ field, errors }: Props) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="Make" error={errors?.make?.message}>
        {({ id, describedBy, invalid }) => <Input id={id} placeholder="Maruti Suzuki" aria-describedby={describedBy} aria-invalid={invalid} {...field("make")} />}
      </Field>
      <Field label="Model" error={errors?.model?.message}>
        {({ id, describedBy, invalid }) => <Input id={id} placeholder="Dzire" aria-describedby={describedBy} aria-invalid={invalid} {...field("model")} />}
      </Field>
      <Field label="Colour" error={errors?.color?.message}>
        {({ id, describedBy, invalid }) => <Input id={id} placeholder="White" aria-describedby={describedBy} aria-invalid={invalid} {...field("color")} />}
      </Field>
      <Field label="Plate number" error={errors?.plateNumber?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} placeholder="TS09AB1234" className="uppercase" aria-describedby={describedBy} aria-invalid={invalid} {...field("plateNumber")} />
        )}
      </Field>
      <Field label="Model year" error={errors?.modelYear?.message}>
        {({ id, describedBy, invalid }) => <Input id={id} type="number" inputMode="numeric" aria-describedby={describedBy} aria-invalid={invalid} {...field("modelYear")} />}
      </Field>
      <Field label="Seats" error={errors?.seats?.message}>
        {({ id, describedBy, invalid }) => <Input id={id} type="number" inputMode="numeric" aria-describedby={describedBy} aria-invalid={invalid} {...field("seats")} />}
      </Field>
      <Field label="Category" error={errors?.category?.message}>
        {({ id, describedBy, invalid }) => (
          <Select id={id} aria-describedby={describedBy} aria-invalid={invalid} {...field("category")}>
            {CATEGORIES.map((category) => <option key={category.value} value={category.value}>{category.label}</option>)}
          </Select>
        )}
      </Field>
    </div>
  );
}
