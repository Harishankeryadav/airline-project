import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../App.jsx";
import { fail, flight, mockGateway, ok, signInAs } from "./helpers.js";

const nav = () => within(screen.getByRole("navigation", { name: "Main" }));
const baseHandlers = {
  "GET /flightsservice/api/v1/flights": () => ok([flight()]),
  "GET /flightsservice/api/v1/city": () => ok([]),
  "GET /flightsservice/api/v1/airplanes": () => ok([]),
};

describe("App", () => {
  it("shows a visitor the public tabs only", async () => {
    mockGateway(baseHandlers);
    render(<App />);
    await screen.findByText("AI-101");

    expect(nav().getByRole("button", { name: "Flights" })).toBeInTheDocument();
    expect(nav().getByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(nav().queryByRole("button", { name: "Administration" })).not.toBeInTheDocument();
  });

  it("creates an account, signs in straight away and returns to the flights", async () => {
    const token = signInAs({ roles: ["CUSTOMER"] }); // what auth-service will return for the new account
    sessionStorage.removeItem("airline.token");
    const calls = mockGateway({
      ...baseHandlers,
      "POST /authservice/api/v1/signup": () => ok({ id: 5, email: "ana@example.com", roles: ["CUSTOMER"] }),
      "POST /authservice/api/v1/signin": () => ok({ token, tokenType: "Bearer", expiresInSeconds: 3600, user: {} }),
    });
    const user = userEvent.setup();
    render(<App />);

    await user.click(nav().getByRole("button", { name: "Sign in" }));
    await user.click(screen.getByRole("tab", { name: "Create account" }));
    await user.type(screen.getByLabelText("Email"), "ana@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account", hidden: false }));

    expect(await screen.findByRole("heading", { name: "Find a flight" })).toBeInTheDocument();
    expect(calls.filter((c) => c.path.startsWith("/authservice")).map((c) => c.path.split("/").pop())).toEqual(["signup", "signin"]);
    expect(nav().getByRole("button", { name: "Account" })).toBeInTheDocument();
  });

  it("shows wrong credentials as a message and stays on the form", async () => {
    mockGateway({
      ...baseHandlers,
      "POST /authservice/api/v1/signin": () => fail(401, "Unauthorized", "Invalid email or password"),
    });
    const user = userEvent.setup();
    render(<App />);

    await user.click(nav().getByRole("button", { name: "Sign in" }));
    await user.type(screen.getByLabelText("Email"), "ana@example.com");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getAllByRole("button", { name: "Sign in" }).at(-1));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
    expect(screen.getByRole("heading", { name: "Sign in" })).toBeInTheDocument();
  });

  it("signs the person out, with a notice, when the server rejects their token", async () => {
    signInAs();
    window.location.hash = "#/bookings";
    mockGateway({
      ...baseHandlers,
      "GET /bookingservice/api/v1/bookings/my": () => fail(401, "Unauthorized", "Missing, invalid or expired token"),
    });
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByText("Sign in to see your bookings")).toBeInTheDocument();
    await user.click(nav().getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Your session has ended. Sign in again.")).toBeInTheDocument();
  });

  it("keeps the person signed in across a page refresh, and signs out on request", async () => {
    signInAs({ email: "ana@example.com" });
    mockGateway(baseHandlers);
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByText("ana@example.com")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /Sign out/ })[0]);
    expect(nav().getByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(sessionStorage.getItem("airline.token")).toBeNull();
  });

  describe("administration by role", () => {
    it("is hidden from customers", async () => {
      signInAs({ roles: ["CUSTOMER"] });
      mockGateway(baseHandlers);
      render(<App />);
      await screen.findByText("AI-101");
      expect(nav().queryByRole("button", { name: "Administration" })).not.toBeInTheDocument();
    });

    it("gives airline business accounts flights and airports only", async () => {
      signInAs({ roles: ["CUSTOMER", "AIRLINE_BUSINESS"] });
      window.location.hash = "#/admin";
      mockGateway(baseHandlers);
      render(<App />);

      expect(await screen.findByRole("heading", { name: "Administration" })).toBeInTheDocument();
      const tabs = screen.getAllByRole("tab").map((t) => t.textContent);
      expect(tabs).toEqual(["Flights and airports"]);
      expect(await screen.findByRole("heading", { name: "Add a flight" })).toBeInTheDocument();
    });

    it("gives administrators every section", async () => {
      signInAs({ roles: ["ADMIN", "CUSTOMER"] });
      window.location.hash = "#/admin";
      mockGateway(baseHandlers);
      render(<App />);

      await screen.findByRole("heading", { name: "Administration" });
      expect(screen.getAllByRole("tab").map((t) => t.textContent))
        .toEqual(["Flights and airports", "All bookings", "Emails", "Users"]);
    });
  });
});
