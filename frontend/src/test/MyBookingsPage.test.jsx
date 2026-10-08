import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MyBookingsPage from "../pages/MyBookingsPage.jsx";
import { AuthProvider } from "../lib/auth.jsx";
import { booking, inDays, mockGateway, ok, signInAs } from "./helpers.js";

function renderPage(navigate = vi.fn()) {
  render(<AuthProvider><MyBookingsPage navigate={navigate} /></AuthProvider>);
  return navigate;
}

describe("MyBookingsPage", () => {
  it("asks a visitor to sign in", () => {
    renderPage();
    expect(screen.getByText("Sign in to see your bookings")).toBeInTheDocument();
  });

  it("lists my bookings and why a failed one did not go through", async () => {
    signInAs();
    mockGateway({
      "GET /bookingservice/api/v1/bookings/my": () =>
        ok([booking(), booking({ id: 13, status: "FAILED", failureReason: "Only 1 seat(s) left" })]),
    });
    renderPage();

    expect(await screen.findByText("Booking #12", { exact: false })).toBeInTheDocument();
    expect(screen.getByText("Confirmed")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText(/Not completed: Only 1 seat\(s\) left/)).toBeInTheDocument();
  });

  it("cancels after a second confirmation, and the booking then shows as cancelled", async () => {
    signInAs();
    const calls = mockGateway({
      "GET /bookingservice/api/v1/bookings/my": () => ok([booking()]),
      "POST /bookingservice/api/v1/bookings/12/cancel": () => ok(booking({ status: "CANCELLED" })),
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Cancel booking" }));
    expect(calls.some((c) => c.method === "POST")).toBe(false);               // one click is not enough
    await user.click(screen.getByRole("button", { name: "Yes, cancel booking" }));

    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel booking" })).not.toBeInTheDocument();
  });

  it("lets the person back out of a cancellation", async () => {
    signInAs();
    const calls = mockGateway({ "GET /bookingservice/api/v1/bookings/my": () => ok([booking()]) });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Cancel booking" }));
    await user.click(screen.getByRole("button", { name: "Keep booking" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(calls.filter((c) => c.method === "POST")).toHaveLength(0);
  });

  it("offers no cancel for bookings that already left or are not confirmed", async () => {
    signInAs();
    mockGateway({
      "GET /bookingservice/api/v1/bookings/my": () =>
        ok([booking({ id: 1, departureTime: new Date(Date.now() - 3600000).toISOString() }),
            booking({ id: 2, status: "CANCELLED", departureTime: inDays(3) })]),
    });
    renderPage();
    await screen.findByText("Booking #1", { exact: false });
    expect(screen.queryByRole("button", { name: "Cancel booking" })).not.toBeInTheDocument();
  });

  it("invites the first booking when there are none", async () => {
    signInAs();
    mockGateway({ "GET /bookingservice/api/v1/bookings/my": () => ok([]) });
    const user = userEvent.setup();
    const navigate = renderPage();

    await user.click(await screen.findByRole("button", { name: "Find a flight" }));
    expect(navigate).toHaveBeenCalledWith("search");
  });
});
