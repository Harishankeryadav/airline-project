import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AirportPicker from "../components/AirportPicker.jsx";
import { bengaluru, delhi, mockGateway, ok } from "./helpers.js";

function Harness() {
  const [value, setValue] = useState(null);
  return (
    <>
      <AirportPicker label="From" value={value} onChange={setValue} />
      <output data-testid="picked">{value ? value.code : "none"}</output>
    </>
  );
}

describe("AirportPicker", () => {
  it("looks airports up by what was typed and lets you pick one", async () => {
    const calls = mockGateway({ "GET /flightsservice/api/v1/airports/search": () => ok([delhi]) });
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "From" }), "del");
    const option = await screen.findByRole("option", { name: /Delhi/ });
    expect(calls.at(-1).path).toContain("/airports/search?q=del");

    await user.click(option);
    expect(screen.getByTestId("picked")).toHaveTextContent("DEL");
    expect(screen.getByRole("button", { name: "Clear From" })).toBeInTheDocument();
  });

  it("does not search for a single character", async () => {
    const calls = mockGateway({ "GET /flightsservice/api/v1/airports/search": () => ok([]) });
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "From" }), "d");
    await new Promise((resolve) => setTimeout(resolve, 400)); // longer than the debounce
    expect(calls).toHaveLength(0);
  });

  it("can be driven from the keyboard: arrows to move, Enter to choose", async () => {
    mockGateway({ "GET /flightsservice/api/v1/airports/search": () => ok([bengaluru, delhi]) });
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "From" }), "in");
    await screen.findAllByRole("option");
    await user.keyboard("{ArrowDown}{Enter}");
    expect(screen.getByTestId("picked")).toHaveTextContent("DEL"); // second option
  });

  it("tells the person when nothing matches", async () => {
    mockGateway({ "GET /flightsservice/api/v1/airports/search": () => ok([]) });
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "From" }), "zzz");
    expect(await screen.findByText("No airports match that text.")).toBeInTheDocument();
  });

  it("clears the choice again", async () => {
    mockGateway({ "GET /flightsservice/api/v1/airports/search": () => ok([delhi]) });
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByRole("combobox", { name: "From" }), "del");
    await user.click(await screen.findByRole("option"));
    await user.click(screen.getByRole("button", { name: "Clear From" }));
    expect(screen.getByTestId("picked")).toHaveTextContent("none");
    expect(screen.getByRole("combobox", { name: "From" })).toBeInTheDocument();
  });

  it("shows the code and city of a value passed in", () => {
    render(<AirportPicker label="To" value={delhi} onChange={vi.fn()} />);
    expect(screen.getByText("DEL")).toBeInTheDocument();
    expect(screen.getByText("Delhi")).toBeInTheDocument();
  });
});
