import { describe, expect, it } from "vitest";
import { driverProfileSchema, passwordSchema, registerSchema } from "./validation";

/** Mirrors backend StrongPasswordValidator: 10 to 72 UTF-8 bytes, at least one letter and one digit. */
describe("passwordSchema", () => {
  it.each([
    ["abcdefghi1", true],
    ["abcdefghij", false],
    ["1234567890", false],
    ["abc1", false],
    ["ä".repeat(35) + "1", true],
    ["ä".repeat(36) + "1", false],
  ])("%s → %s", (password, valid) => {
    expect(passwordSchema.safeParse(password).success).toBe(valid);
  });
});

describe("registerSchema", () => {
  const base = { fullName: "Asha Rao", email: "asha@example.com", password: "S3cure-pass!", accountType: "PASSENGER" as const };

  it("accepts an empty phone and an E.164 phone", () => {
    expect(registerSchema.safeParse({ ...base, phone: "" }).success).toBe(true);
    expect(registerSchema.safeParse({ ...base, phone: "+919876543210" }).success).toBe(true);
  });

  it("rejects a local phone format", () => {
    expect(registerSchema.safeParse({ ...base, phone: "9876543210" }).success).toBe(false);
  });
});

describe("driverProfileSchema", () => {
  const vehicle = { make: "Toyota", model: "Innova", color: "Silver", plateNumber: "TS09 AB 1234", modelYear: "2023", category: "XL", seats: "6" };

  it("coerces numeric form input", () => {
    const parsed = driverProfileSchema.parse({ licenseNumber: "TS-0920110012345", vehicle });
    expect(parsed.vehicle.modelYear).toBe(2023);
    expect(parsed.vehicle.seats).toBe(6);
  });

  it("applies the backend's licence and plate patterns", () => {
    expect(driverProfileSchema.safeParse({ licenseNumber: "AB 12", vehicle }).success).toBe(false);
    expect(driverProfileSchema.safeParse({ licenseNumber: "TS0920110012345", vehicle: { ...vehicle, plateNumber: "A!" } }).success).toBe(false);
  });
});
