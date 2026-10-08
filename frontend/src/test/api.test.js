import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, configureApi, messageFromEnvelope, request } from "../lib/api.js";
import { fail, mockGateway, ok } from "./helpers.js";

describe("api client", () => {
  const onUnauthorized = vi.fn();

  beforeEach(() => {
    onUnauthorized.mockReset();
    configureApi({ getToken: () => "the-token", onUnauthorized });
  });

  it("returns the data of a successful envelope", async () => {
    mockGateway({ "GET /flightsservice/api/v1/city": () => ok([{ id: 1, name: "Delhi" }]) });
    await expect(request("/flightsservice/api/v1/city")).resolves.toEqual([{ id: 1, name: "Delhi" }]);
  });

  it("sends the Bearer token only when the call asks for auth", async () => {
    const calls = mockGateway({ "GET /": () => ok(null) });
    await request("/public");
    await request("/private", { auth: true });
    expect(calls[0].headers.Authorization).toBeUndefined();
    expect(calls[1].headers.Authorization).toBe("Bearer the-token");
  });

  it("sends a JSON body with the right content type", async () => {
    const calls = mockGateway({ "POST /x": () => ok({}) });
    await request("/x", { method: "POST", body: { a: 1 } });
    expect(calls[0].headers["Content-Type"]).toBe("application/json");
    expect(calls[0].body).toEqual({ a: 1 });
  });

  it("shows the detail of an error envelope, which is the useful sentence", async () => {
    mockGateway({ "POST /b": () => fail(409, "Conflict", "Only 2 seat(s) left on flight AI-101, requested 5") });
    await expect(request("/b", { method: "POST", body: {} })).rejects.toMatchObject({
      status: 409, message: "Only 2 seat(s) left on flight AI-101, requested 5",
    });
  });

  it("joins field errors from a validation failure", () => {
    const data = { success: false, message: "Validation failed", err: { email: "must be valid", password: "too short" } };
    expect(messageFromEnvelope(data, 400)).toBe("email: must be valid; password: too short");
  });

  it("explains an empty 429 from the rate limiter", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 429, json: async () => { throw new Error("empty"); } })));
    await expect(request("/x")).rejects.toThrow(/Too many requests/);
  });

  it("explains an unreachable gateway", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("Failed to fetch"); }));
    const error = await request("/x").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
    expect(error.message).toMatch(/Cannot reach the API gateway at http:\/\/localhost:8080/);
  });

  it("signs the person out when a request that carried a token gets 401", async () => {
    mockGateway({ "GET /p": () => fail(401, "Unauthorized", "Missing, invalid or expired token") });
    await expect(request("/p", { auth: true })).rejects.toThrow(/session has ended/);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("does NOT treat wrong credentials (401 without a token) as an expired session", async () => {
    mockGateway({ "POST /signin": () => fail(401, "Unauthorized", "Invalid email or password") });
    await expect(request("/signin", { method: "POST", body: {} })).rejects.toThrow("Invalid email or password");
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("lets a cancelled request through as an AbortError, not as a user-facing error", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new DOMException("Aborted", "AbortError"); }));
    const error = await request("/x").catch((e) => e);
    expect(error.name).toBe("AbortError");
  });
});
