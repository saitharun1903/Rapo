"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { VehicleFields } from "@/components/driver/VehicleFields";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Badge, Card, CardTitle, ErrorState, LoadingBlock, PageHeader, type Tone } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { DriverResponse, DriverVerificationStatus } from "@/lib/api/types";
import { formatDateTime, formatRating, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { driverProfileSchema, vehicleSchema, type DriverProfileForm, type VehicleForm } from "@/lib/validation";

const VERIFICATION_TONES: Record<DriverVerificationStatus, Tone> = {
  PENDING: "warning",
  VERIFIED: "success",
  REJECTED: "danger",
  SUSPENDED: "danger",
};

const EMPTY_VEHICLE: VehicleForm = { make: "", model: "", color: "", plateNumber: "", modelYear: "", category: "ECONOMY", seats: "4" };
const replaceSchema = z.object({ vehicle: vehicleSchema });

function OnboardingForm({ onDone }: { onDone: (driver: DriverResponse) => void }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<DriverProfileForm, unknown, z.output<typeof driverProfileSchema>>({
    resolver: zodResolver(driverProfileSchema),
    defaultValues: { licenseNumber: "", vehicle: EMPTY_VEHICLE },
  });
  const onSubmit = handleSubmit(async (values) => {
    try {
      onDone(await unwrap(api.POST("/api/drivers/me/profile", { body: values })));
      toast.success("Submitted. An admin will review your profile.");
    } catch (error) {
      if (isApiError(error, "LICENSE_TAKEN")) {
        setError("licenseNumber", { message: "This licence number is already registered." });
      } else if (isApiError(error, "PLATE_TAKEN")) {
        setError("vehicle.plateNumber", { message: "This plate is already registered." });
      } else {
        toast.error(errorMessage(error));
      }
    }
  });
  return (
    <Card>
      <CardTitle>Licence and vehicle</CardTitle>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Field label="Driving licence number" error={errors.licenseNumber?.message}>
          {({ id, describedBy, invalid }) => <Input id={id} className="uppercase" aria-describedby={describedBy} aria-invalid={invalid} {...register("licenseNumber")} />}
        </Field>
        <VehicleFields field={(name) => register(`vehicle.${name}`)} errors={errors.vehicle} />
        <Button type="submit" size="lg" loading={isSubmitting}>Submit for verification</Button>
      </form>
    </Card>
  );
}

function ReplaceVehicleForm({ driver, onDone }: { driver: DriverResponse; onDone: (driver: DriverResponse) => void }) {
  const { register, handleSubmit, setError, reset, formState: { errors } } = useForm<{ vehicle: VehicleForm }, unknown, z.output<typeof replaceSchema>>({
    resolver: zodResolver(replaceSchema),
    defaultValues: { vehicle: EMPTY_VEHICLE },
  });
  const replace = useMutation({
    mutationFn: (vehicle: z.output<typeof vehicleSchema>) => unwrap(api.PUT("/api/drivers/me/vehicle", { body: vehicle })),
    onSuccess: (updated) => {
      onDone(updated);
      reset();
      toast.success("Vehicle updated.");
    },
    onError: (error) => {
      if (isApiError(error, "PLATE_TAKEN")) {
        setError("vehicle.plateNumber", { message: "This plate was already registered." });
      } else {
        toast.error(errorMessage(error));
      }
    },
  });
  const offline = driver.availability === "OFFLINE";
  return (
    <Card>
      <CardTitle>Replace vehicle</CardTitle>
      {!offline && <p className="mb-3 rounded-xl bg-warning-soft px-3 py-2 text-sm text-warning">Go offline before changing your vehicle.</p>}
      <form onSubmit={handleSubmit(({ vehicle }) => replace.mutate(vehicle))} noValidate className="flex flex-col gap-4">
        <fieldset disabled={!offline} className="contents">
          <VehicleFields field={(name) => register(`vehicle.${name}`)} errors={errors.vehicle} />
        </fieldset>
        <Button type="submit" variant="secondary" disabled={!offline} loading={replace.isPending}>Save new vehicle</Button>
      </form>
    </Card>
  );
}

export default function OnboardingPage() {
  const queryClient = useQueryClient();
  const [justSubmitted, setJustSubmitted] = useState(false);
  const profile = useQuery({
    queryKey: queryKeys.driverProfile,
    queryFn: () => unwrap(api.GET("/api/drivers/me")),
  });
  const store = (driver: DriverResponse) => queryClient.setQueryData(queryKeys.driverProfile, driver);

  if (profile.isPending) {
    return <div className="mx-auto max-w-3xl p-6"><LoadingBlock label="Loading your profile" /></div>;
  }
  if (profile.isError && !isApiError(profile.error, "DRIVER_PROFILE_NOT_FOUND")) {
    return <div className="mx-auto max-w-3xl p-6"><ErrorState error={profile.error} onRetry={() => void profile.refetch()} /></div>;
  }
  const driver = profile.data;

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <PageHeader title={driver ? "Profile and vehicle" : "Become a RideFlow driver"}
        description={driver ? undefined : "Tell us about your licence and vehicle. An admin verifies them before your first ride."} />
      {!driver && <OnboardingForm onDone={(created) => { setJustSubmitted(true); store(created); }} />}
      {driver && (
        <div className="flex flex-col gap-4">
          <Card>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-sm text-fg-muted">Verification</p>
                <Badge tone={VERIFICATION_TONES[driver.verificationStatus]}>{humanize(driver.verificationStatus)}</Badge>
              </div>
              <div className="text-right text-sm">
                <p className="text-fg-muted">Rating</p>
                <p className="font-semibold">{formatRating(driver.ratingAvg)} {driver.ratingCount > 0 && `(${driver.ratingCount})`}</p>
              </div>
            </div>
            {justSubmitted && driver.verificationStatus === "PENDING" && (
              <p className="mt-3 text-sm text-fg-muted">Thanks! You will get a notification when an admin has reviewed your profile.</p>
            )}
            {driver.rejectionReason && <p className="mt-3 text-sm text-danger">Reason: {driver.rejectionReason}</p>}
            {driver.verifiedAt && <p className="mt-3 text-sm text-fg-muted">Verified {formatDateTime(driver.verifiedAt)}</p>}
            <p className="mt-3 text-sm text-fg-muted">Licence {driver.licenseNumber}</p>
          </Card>
          {driver.vehicle && (
            <Card>
              <CardTitle>Current vehicle</CardTitle>
              <p className="font-semibold">{driver.vehicle.color} {driver.vehicle.make} {driver.vehicle.model} ({driver.vehicle.modelYear})</p>
              <p className="text-sm text-fg-muted">{driver.vehicle.plateNumber} · {humanize(driver.vehicle.category)} · {driver.vehicle.seats} seats</p>
            </Card>
          )}
          <ReplaceVehicleForm driver={driver} onDone={store} />
        </div>
      )}
    </div>
  );
}
