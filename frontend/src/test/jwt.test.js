import { describe, expect, it } from "vitest";
import { userFromToken } from "../lib/jwt.js";
import { makeToken } from "./helpers.js";

describe("userFromToken", () => {
  it("reads id, email and roles from an auth-service token", () => {
    const user = userFromToken(makeToken({ sub: "42", email: "bo@example.com", roles: ["ADMIN", "CUSTOMER"] }));
    expect(user).toMatchObject({ id: 42, email: "bo@example.com", roles: ["ADMIN", "CUSTOMER"] });
  });

  it("rejects an expired token", () => {
    expect(userFromToken(makeToken({ expiresInSeconds: -10 }))).toBeNull();
  });

  it("rejects garbage, empty input and tokens without the expected claims", () => {
    expect(userFromToken("not-a-token")).toBeNull();
    expect(userFromToken("")).toBeNull();
    expect(userFromToken(null)).toBeNull();
    expect(userFromToken(`${btoa("{}")}.${btoa(JSON.stringify({ sub: "1" }))}.x`)).toBeNull(); // no email / exp
  });

  it("treats a missing roles claim as no roles", () => {
    const payload = btoa(JSON.stringify({ sub: "1", email: "a@b.c", exp: Math.floor(Date.now() / 1000) + 60 }));
    expect(userFromToken(`h.${payload}.s`).roles).toEqual([]);
  });
});
