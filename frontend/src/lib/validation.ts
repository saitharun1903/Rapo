import { z } from "zod";

/**
 * Client-side mirrors of the backend's validation rules (docs/api.md), so most mistakes are caught before a
 * round trip. The backend still validates everything; its field errors are shown when these miss something.
 */
const PASSWORD_MIN_BYTES = 10;
/** BCrypt reads only the first 72 bytes, so the backend rejects longer passwords. */
const PASSWORD_MAX_BYTES = 72;
const EMAIL_MAX = 254;
const NAME_MAX = 120;
const E164_PHONE = /^\+[1-9]\d{7,14}$/;
const LETTER = /\p{L}/u;
const DIGIT = /\p{Nd}/u;
const encoder = new TextEncoder();

export const passwordSchema = z.string()
  .refine((value) => encoder.encode(value).length >= PASSWORD_MIN_BYTES, "Use at least 10 characters")
  .refine((value) => encoder.encode(value).length <= PASSWORD_MAX_BYTES, "Use at most 72 characters")
  .refine((value) => LETTER.test(value) && DIGIT.test(value), "Include at least one letter and one digit");

const optionalPhone = z.string().trim()
  .refine((value) => value === "" || E164_PHONE.test(value), "Use international format, e.g. +919876543210");

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Enter your email").max(EMAIL_MAX),
  password: z.string().min(1, "Enter your password"),
});

export const registerSchema = z.object({
  fullName: z.string().trim().min(1, "Enter your name").max(NAME_MAX),
  email: z.email("Enter a valid email").max(EMAIL_MAX),
  phone: optionalPhone,
  password: passwordSchema,
  accountType: z.enum(["PASSENGER", "DRIVER"]),
});

export const profileSchema = z.object({
  fullName: z.string().trim().min(1, "Enter your name").max(NAME_MAX),
  phone: optionalPhone,
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Enter your current password"),
  newPassword: passwordSchema,
});

const CURRENT_YEAR = new Date().getFullYear();
const FIRST_MODEL_YEAR = 1990;
/** Next year's models go on sale during the current year; the backend allows one year ahead. */
const MODEL_YEARS_AHEAD = 1;
const PLATE = /^[A-Za-z0-9 -]{4,20}$/;
const MIN_SEATS = 2;
const MAX_SEATS = 8;

export const vehicleSchema = z.object({
  make: z.string().trim().min(1, "Required").max(40),
  model: z.string().trim().min(1, "Required").max(40),
  color: z.string().trim().min(1, "Required").max(30),
  plateNumber: z.string().trim().regex(PLATE, "4–20 letters, digits, spaces or dashes"),
  modelYear: z.coerce.number<string>().int().min(FIRST_MODEL_YEAR, `From ${FIRST_MODEL_YEAR}`)
    .max(CURRENT_YEAR + MODEL_YEARS_AHEAD, `At most ${CURRENT_YEAR + MODEL_YEARS_AHEAD}`),
  category: z.enum(["ECONOMY", "COMFORT", "XL"]),
  seats: z.coerce.number<string>().int().min(MIN_SEATS).max(MAX_SEATS),
});

const LICENSE = /^[A-Za-z0-9-]{6,40}$/;

export const driverProfileSchema = z.object({
  licenseNumber: z.string().trim().regex(LICENSE, "6–40 letters, digits or dashes"),
  vehicle: vehicleSchema,
});

const REASON_MAX = 255;

export const reasonSchema = z.object({
  reason: z.string().trim().min(1, "Give a reason").max(REASON_MAX),
});

export type LoginForm = z.infer<typeof loginSchema>;
export type RegisterForm = z.infer<typeof registerSchema>;
export type ProfileForm = z.infer<typeof profileSchema>;
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>;
export type VehicleForm = z.input<typeof vehicleSchema>;
export type DriverProfileForm = z.input<typeof driverProfileSchema>;
