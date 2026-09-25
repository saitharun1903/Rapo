import { expect, type APIRequestContext } from "@playwright/test";
import type { Point } from "./support";

type Quote = { quoteId: string; vehicleCategory: string };

/**
 * A passenger driven through the REST API (through the frontend's /api proxy), for tests whose subject is
 * someone else, such as the driver console.
 */
export class PassengerApi {
  private token = "";

  private constructor(private readonly request: APIRequestContext) {}

  static async signIn(request: APIRequestContext, email: string, password: string): Promise<PassengerApi> {
    const passenger = new PassengerApi(request);
    const response = await request.post("/api/auth/login", { data: { email, password } });
    expect(response.status(), await response.text()).toBe(200);
    passenger.token = ((await response.json()) as { accessToken: string }).accessToken;
    return passenger;
  }

  private get headers() {
    return { Authorization: `Bearer ${this.token}` };
  }

  /** Books an economy ride paid in cash; returns its id. */
  async book(pickup: Point, dropoff: Point): Promise<string> {
    const estimate = await this.request.post("/api/fares/estimate", { headers: this.headers, data: { pickup, dropoff } });
    expect(estimate.status(), await estimate.text()).toBe(200);
    const { quotes } = (await estimate.json()) as { quotes: Quote[] };
    const quote = quotes.find((candidate) => candidate.vehicleCategory === "ECONOMY");
    expect(quote, "an economy quote").toBeDefined();

    const ride = await this.request.post("/api/rides", {
      headers: this.headers,
      data: {
        quoteId: quote!.quoteId,
        pickup: { point: pickup, address: "E2E pickup" },
        dropoff: { point: dropoff, address: "E2E destination" },
        paymentMethod: "CASH",
      },
    });
    expect(ride.status(), await ride.text()).toBe(201);
    return ((await ride.json()) as { id: string }).id;
  }

  async rideStatus(rideId: string): Promise<string> {
    const response = await this.request.get(`/api/rides/${rideId}`, { headers: this.headers });
    expect(response.status(), await response.text()).toBe(200);
    return ((await response.json()) as { status: string }).status;
  }
}
