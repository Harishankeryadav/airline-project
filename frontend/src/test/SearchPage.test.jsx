import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SearchPage from "../pages/SearchPage.jsx";
import { AuthProvider } from "../lib/auth.jsx";
import { booking, bengaluru, fail, flight, mockGateway, ok, signInAs } from "./helpers.js";

function renderSearch(navigate = vi.fn()) {
  render(<AuthProvider><SearchPage navigate={navigate} /></AuthProvider>);
  return navigate;
}

describe("SearchPage", () => {
  it("shows the next departures straight away, as a board", async () => {
    mockGateway({ "GET /flightsservice/api/v1/flights": () => ok([flight(), flight({ id: 2, flightNumber: "6E-201" })]) });
    renderSearch();

    expect(await screen.findByText("AI-101")).toBeInTheDocument();
    expect(screen.getByText("6E-201")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Next departures" })).toBeInTheDocument();
    const firstRow = screen.getAllByRole("listitem")[0];
    expect(firstRow.querySelector(".route-code")).toHaveTextContent(/BLR\s*to\s*DEL/); // the route code on the board
    expect(within(firstRow).getByText("Bengaluru to Delhi")).toBeInTheDocument(); // and the city names under it
  });

  it("searches with the chosen airports and filters", async () => {
    const calls = mockGateway({
      "GET /flightsservice/api/v1/airports/search": () => ok([bengaluru]),
      "GET /flightsservice/api/v1/flights": () => ok([flight()]),
    });
    const user = userEvent.setup();
    renderSearch();
    await screen.findByText("AI-101");

    await user.type(screen.getByRole("combobox", { name: "From" }), "beng");
    await user.click(await screen.findByRole("option", { name: /Bengaluru/ }));
    await user.type(screen.getByLabelText("Highest fare"), "6000");
    await user.selectOptions(screen.getByLabelText("Sort by"), "cheapest");
    await user.click(screen.getByRole("button", { name: /Search flights/ }));

    await screen.findByRole("heading", { name: "Matching flights" });
    const search = calls.filter((c) => c.path.startsWith("/flightsservice/api/v1/flights")).at(-1).path;
    expect(search).toContain("departureAirportId=1");
    expect(search).toContain("maxPrice=6000");
    expect(search).toContain("sortBy=price");
  });

  it("explains an empty result", async () => {
    mockGateway({ "GET /flightsservice/api/v1/flights": () => ok([]) });
    renderSearch();
    expect(await screen.findByText("No flights scheduled yet")).toBeInTheDocument();
  });

  it("shows a backend error instead of an empty board", async () => {
    mockGateway({ "GET /flightsservice/api/v1/flights": () => fail(503, "Service unavailable", "The requested service is currently unavailable, please try again") });
    renderSearch();
    expect(await screen.findByRole("alert")).toHaveTextContent("currently unavailable");
  });

  describe("booking", () => {
    it("books for the signed-in user without sending a userId, then refreshes the seat count", async () => {
      const token = signInAs({ sub: "5", email: "ana@example.com" });
      const calls = mockGateway({
        "GET /flightsservice/api/v1/flights/1": () => ok(flight({ availableSeats: 298 })),
        "GET /flightsservice/api/v1/flights": () => ok([flight(), flight({ id: 2, flightNumber: "6E-201" })]),
        "POST /bookingservice/api/v1/bookings": () => ok(booking()),
      });
      const user = userEvent.setup();
      renderSearch();
      await screen.findByText("AI-101");

      const seats = screen.getByLabelText("Seats on AI-101");
      await user.clear(seats);
      await user.type(seats, "2");
      await user.click(screen.getAllByRole("button", { name: "Book" })[0]);

      expect(await screen.findByText(/Booking #12 is confirmed/)).toBeInTheDocument();
      const post = calls.find((c) => c.method === "POST");
      expect(post.body).toEqual({ flightId: 1, noOfSeats: 2 });           // no userId: the server uses the token
      expect(post.headers.Authorization).toBe(`Bearer ${token}`);
      expect(await screen.findByText("298 seats")).toBeInTheDocument();    // seat count refreshed
    });

    it("shows the reason when there are not enough seats", async () => {
      signInAs();
      mockGateway({
        "GET /flightsservice/api/v1/flights": () => ok([flight({ availableSeats: 50 })]),
        "POST /bookingservice/api/v1/bookings": () =>
          fail(409, "Conflict", "Only 1 seat(s) left on flight AI-101, requested 2"),
      });
      const user = userEvent.setup();
      renderSearch();
      await screen.findByText("AI-101");

      await user.click(screen.getByRole("button", { name: "Book" }));
      expect(await screen.findByRole("alert")).toHaveTextContent("Only 1 seat(s) left on flight AI-101, requested 2");
    });

    it("asks a visitor to sign in instead of booking", async () => {
      mockGateway({ "GET /flightsservice/api/v1/flights": () => ok([flight()]) });
      const user = userEvent.setup();
      const navigate = renderSearch();
      await screen.findByText("AI-101");

      expect(screen.queryByRole("button", { name: "Book" })).not.toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Sign in to book" }));
      expect(navigate).toHaveBeenCalledWith("account");
    });

    it("offers no booking for sold-out or departed flights", async () => {
      signInAs();
      mockGateway({
        "GET /flightsservice/api/v1/flights": () =>
          ok([
            flight({ id: 1, flightNumber: "FULL-1", availableSeats: 0 }),
            flight({ id: 2, flightNumber: "GONE-2", departureTime: new Date(Date.now() - 3600000).toISOString() }),
          ]),
      });
      renderSearch();
      await screen.findByText("FULL-1");

      expect(screen.queryByRole("button", { name: "Book" })).not.toBeInTheDocument();
      const rows = screen.getAllByRole("listitem");
      expect(within(rows[0]).getAllByText("Sold out").length).toBeGreaterThan(0);
      expect(within(rows[1]).getByText("Departed")).toBeInTheDocument();
    });
  });
});
