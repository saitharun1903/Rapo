import type { components } from "./schema";

/** Short names for the schemas generated from docs/openapi.json (npm run api:types). */
type Schemas = components["schemas"];

export type ApiErrorBody = {
  status: number;
  error: string;
  code: string;
  message: string;
  path?: string;
  traceId?: string;
  timestamp?: string;
  fieldErrors?: { field: string; message: string }[];
};

export type Money = Schemas["Money"];
export type GeoPoint = Schemas["GeoPoint"];
export type UserResponse = Schemas["UserResponse"];
export type Role = UserResponse["role"];
export type AuthResponse = Schemas["AuthResponse"];
export type RegisterRequest = Schemas["RegisterRequest"];

export type PlaceResponse = Schemas["PlaceResponse"];
export type RouteResponse = Schemas["RouteResponse"];
export type FareEstimateResponse = Schemas["FareEstimateResponse"];
export type FareQuoteResponse = Schemas["FareQuoteResponse"];
export type FareBreakdown = Schemas["FareBreakdownResponse"];

export type RideResponse = Schemas["RideResponse"];
export type RideStatus = RideResponse["status"];
export type VehicleCategory = RideResponse["vehicleCategory"];
export type PaymentMethod = RideResponse["paymentMethod"];
export type RideSummary = Schemas["RideSummaryResponse"];
export type RideTimelineEntry = Schemas["RideTimelineEntryResponse"];
export type RideTracking = Schemas["RideTrackingResponse"];
export type Eta = Schemas["EtaResponse"];
export type RideOffer = Schemas["RideOfferResponse"];
export type NearbyDriver = Schemas["NearbyDriverResponse"];

export type DriverResponse = Schemas["DriverResponse"];
export type VehicleRequest = Schemas["VehicleRequest"];
export type EarningsResponse = Schemas["EarningsResponse"];
export type ReportGranularity = EarningsResponse["granularity"];

export type TripAnalysis = Schemas["TripAnalysisResponse"];
export type TripQuestion = Schemas["TripQuestionResponse"];

export type NotificationItem = Schemas["NotificationResponse"];

export type AdminOverview = Schemas["AdminOverviewResponse"];
export type RideActivity = Schemas["RideActivityResponse"];
export type AdminRideSummary = Schemas["AdminRideSummaryResponse"];
export type AdminRideDetail = Schemas["AdminRideDetailResponse"];
export type SystemStatus = Schemas["SystemStatusResponse"];
export type AuditLogEntry = Schemas["AuditLogResponse"];
export type AuditAction = AuditLogEntry["action"];
export type UserStatus = UserResponse["status"];
export type DriverVerificationStatus = DriverResponse["verificationStatus"];

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
};
