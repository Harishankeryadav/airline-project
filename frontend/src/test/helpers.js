import { vi } from "vitest";

/** An unsigned token shaped like auth-service's: the app only decodes it, the servers verify the signature. */
export function makeToken({ sub = "5", email = "ana@example.com", roles = ["CUSTOMER"], expiresInSeconds = 3600 } = {}) {
  const b64 = (object) => btoa(JSON.stringify(object)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
  return `${b64({ alg: "HS256", typ: "JWT" })}.${b64({ sub, email, roles, exp })}.signature`;
}

export function signInAs(options) {
  const token = makeToken(options);
  sessionStorage.setItem("airline.token", token);
  return token;
}

export const ok = (data) => ({ status: 200, body: { success: true, message: "ok", data, err: {} } });
export const fail = (status, message, err) => ({ status, body: { success: false, message, data: null, err } });

/**
 * Replaces fetch with a fake api-gateway. Handlers are keyed "METHOD /path-prefix"; the first match wins.
 * Returns the list of calls made, so tests can assert on URLs, headers and bodies.
 */
export function mockGateway(handlers) {
  const calls = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, init = {}) => {
      const method = init.method || "GET";
      const path = String(url).replace(/^https?:\/\/[^/]+/, "");
      const call = { method, path, headers: init.headers || {}, body: init.body ? JSON.parse(init.body) : undefined };
      calls.push(call);
      const key = Object.keys(handlers).find((k) => {
        const [m, ...rest] = k.split(" ");
        return m === method && path.startsWith(rest.join(" "));
      });
      const result = key ? await handlers[key](call) : fail(404, "Not found", `no handler for ${method} ${path}`);
      return { ok: result.status >= 200 && result.status < 300, status: result.status, json: async () => result.body };
    })
  );
  return calls;
}

export const inDays = (days) => new Date(Date.now() + days * 86400000).toISOString();

export function flight(overrides = {}) {
  return {
    id: 1,
    flightNumber: "AI-101",
    airplaneId: 1,
    airplaneModel: "Boeing 737",
    departureAirportId: 1,
    arrivalAirportId: 3,
    departureAirport: { id: 1, name: "Kempegowda International Airport", code: "BLR", address: null, cityId: 1, cityName: "Bengaluru" },
    arrivalAirport: { id: 3, name: "Indira Gandhi International Airport", code: "DEL", address: null, cityId: 3, cityName: "Delhi" },
    departureTime: inDays(7),
    arrivalTime: new Date(Date.now() + 7 * 86400000 + 170 * 60000).toISOString(),
    price: 5200,
    boardingGate: "A12",
    totalSeats: 300,
    availableSeats: 300,
    ...overrides,
  };
}

export function booking(overrides = {}) {
  return {
    id: 12, userId: 5, flightId: 1, flightNumber: "AI-101", origin: "Bengaluru (BLR)", destination: "Delhi (DEL)",
    departureTime: inDays(7), noOfSeats: 2, unitPrice: 5200, totalCost: 10400, status: "CONFIRMED",
    failureReason: null, createdAt: new Date().toISOString(), ...overrides,
  };
}

export const bengaluru = flight().departureAirport;
export const delhi = flight().arrivalAirport;
