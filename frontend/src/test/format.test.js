import { describe, expect, it } from "vitest";
import {
  airportCode, formatDuration, formatPrice, isPast, localInputToUtcIso, sentenceCase, seatStatus, statusTone,
} from "../lib/format.js";

describe("format helpers", () => {
  it("formats flight durations", () => {
    expect(formatDuration("2026-11-01T09:00:00Z", "2026-11-01T11:50:00Z")).toBe("2h 50m");
    expect(formatDuration("2026-11-01T09:00:00Z", "2026-11-01T09:45:00Z")).toBe("45m");
    expect(formatDuration("2026-11-01T09:00:00Z", "2026-11-01T09:00:00Z")).toBe("-");
    expect(formatDuration("bad", "worse")).toBe("-");
  });

  it("formats fares as currency and survives bad input", () => {
    expect(formatPrice(10400, "INR")).toMatch(/10,?400/);
    expect(formatPrice("5200.50", "USD")).toMatch(/5,?200\.50/);
    expect(formatPrice(undefined)).toBe("-");
  });

  it("describes seat availability", () => {
    expect(seatStatus(0)).toEqual({ text: "Sold out", tone: "bad" });
    expect(seatStatus(3)).toEqual({ text: "3 left", tone: "warn" });
    expect(seatStatus(120)).toEqual({ text: "120 seats", tone: "ok" });
  });

  it("uses the IATA code, or the city when an airport has none", () => {
    expect(airportCode({ code: "BLR", cityName: "Bengaluru" })).toBe("BLR");
    expect(airportCode({ code: null, cityName: "Mysuru" })).toBe("Mysuru");
    expect(airportCode(undefined)).toBe("-");
  });

  it("turns datetime-local input into UTC ISO, or null when invalid", () => {
    expect(localInputToUtcIso("2026-11-01T09:30")).toMatch(/^2026-11-01T\d\d:\d\d:00\.000Z$|^2026-1[01]-\d\dT/);
    expect(localInputToUtcIso("")).toBeNull();
  });

  it("maps backend enums to readable text and tones", () => {
    expect(sentenceCase("AIRLINE_BUSINESS")).toBe("Airline business");
    expect(statusTone("CONFIRMED")).toBe("ok");
    expect(statusTone("FAILED")).toBe("bad");
    expect(statusTone("PENDING")).toBe("warn");
    expect(statusTone("CANCELLED")).toBe("muted");
    expect(statusTone("???")).toBe("muted");
  });

  it("knows whether a time has passed", () => {
    expect(isPast(new Date(Date.now() - 1000).toISOString())).toBe(true);
    expect(isPast(new Date(Date.now() + 60000).toISOString())).toBe(false);
  });
});
