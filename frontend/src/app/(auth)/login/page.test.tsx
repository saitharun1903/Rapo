import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { login } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { auth } from "@/test/fixtures";
import LoginPage from "./page";

const replace = vi.fn();
let search = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => search,
}));
vi.mock("@/lib/api/client", () => ({ login: vi.fn() }));

async function signIn(email: string, password: string) {
  await userEvent.type(screen.getByLabelText("Email"), email);
  await userEvent.type(screen.getByLabelText("Password"), password);
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

beforeEach(() => {
  replace.mockReset();
  vi.mocked(login).mockReset();
  search = new URLSearchParams();
});

describe("LoginPage", () => {
  it("asks for both fields before calling the server", async () => {
    render(<LoginPage />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Enter your email")).toBeInTheDocument();
    expect(screen.getByText("Enter your password")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(login).not.toHaveBeenCalled();
  });

  it("sends each role to its home", async () => {
    const driver = auth("t");
    vi.mocked(login).mockResolvedValue({ ...driver, user: { ...driver.user, role: "DRIVER" } });
    render(<LoginPage />);
    await signIn(" driver@example.com ", "secret-password-1");

    expect(login).toHaveBeenCalledWith("driver@example.com", "secret-password-1");
    expect(replace).toHaveBeenCalledWith("/drive");
  });

  it("returns to the page that asked for sign-in, but never to another site", async () => {
    vi.mocked(login).mockResolvedValue(auth("t"));
    search = new URLSearchParams({ next: "/trips/r1" });
    const { unmount } = render(<LoginPage />);
    await signIn("a@example.com", "secret-password-1");
    expect(replace).toHaveBeenLastCalledWith("/trips/r1");
    unmount();

    search = new URLSearchParams({ next: "//evil.example.com" });
    render(<LoginPage />);
    await signIn("a@example.com", "secret-password-1");
    expect(replace).toHaveBeenLastCalledWith("/ride");
  });

  it("says when the email and password do not match, and shows other errors as they are", async () => {
    vi.mocked(login).mockRejectedValueOnce(new ApiError(401, "INVALID_CREDENTIALS", "Invalid credentials"));
    render(<LoginPage />);
    await signIn("a@example.com", "wrong-password-1");
    expect(await screen.findByRole("alert")).toHaveTextContent("That email and password do not match.");

    vi.mocked(login).mockRejectedValueOnce(new ApiError(429, "RATE_LIMITED", "Too many attempts. Try again in a minute."));
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Too many attempts. Try again in a minute.");
    expect(replace).not.toHaveBeenCalled();
  });
});
