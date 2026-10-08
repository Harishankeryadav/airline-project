import { request } from "./api.js";

const AUTH = "/authservice/api/v1";
const FLIGHTS = "/flightsservice/api/v1";
const BOOKING = "/bookingservice/api/v1/bookings";
const REMINDER = "/reminderservice/api/v1/tickets";

function query(params) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const authApi = {
  signup: (email, password) => request(`${AUTH}/signup`, { method: "POST", body: { email, password } }),
  signin: (email, password) => request(`${AUTH}/signin`, { method: "POST", body: { email, password } }),
  listUsers: () => request(`${AUTH}/users`, { auth: true }),
  grantRole: (userId, role) => request(`${AUTH}/users/${userId}/roles`, { method: "POST", auth: true, body: { role } }),
  revokeRole: (userId, role) => request(`${AUTH}/users/${userId}/roles/${role}`, { method: "DELETE", auth: true }),
};

export const flightsApi = {
  /** Airport name or city name, partial and case-insensitive. */
  searchAirports: (q, limit = 8, signal) => request(`${FLIGHTS}/airports/search${query({ q, limit })}`, { signal }),
  listCities: () => request(`${FLIGHTS}/city`),
  listAirports: () => request(`${FLIGHTS}/airports`),
  listAirplanes: () => request(`${FLIGHTS}/airplanes`),
  searchFlights: (filters) => request(`${FLIGHTS}/flights${query(filters)}`),
  getFlight: (id) => request(`${FLIGHTS}/flights/${id}`),

  createCity: (name) => request(`${FLIGHTS}/city`, { method: "POST", auth: true, body: { name } }),
  createAirport: (body) => request(`${FLIGHTS}/airports`, { method: "POST", auth: true, body }),
  createAirplane: (body) => request(`${FLIGHTS}/airplanes`, { method: "POST", auth: true, body }),
  createFlight: (body) => request(`${FLIGHTS}/flights`, { method: "POST", auth: true, body }),
};

export const bookingApi = {
  /** The booking belongs to the signed-in user - the server reads who that is from the token. */
  create: ({ flightId, noOfSeats }) => request(BOOKING, { method: "POST", auth: true, body: { flightId, noOfSeats } }),
  mine: () => request(`${BOOKING}/my`, { auth: true }),
  cancel: (id) => request(`${BOOKING}/${id}/cancel`, { method: "POST", auth: true }),
  all: (status) => request(`${BOOKING}${query({ status })}`, { auth: true }),
};

export const reminderApi = {
  list: (status) => request(`${REMINDER}${query({ status })}`, { auth: true }),
  create: (body) => request(REMINDER, { method: "POST", auth: true, body }),
  cancel: (id) => request(`${REMINDER}/${id}/cancel`, { method: "POST", auth: true }),
  retry: (id) => request(`${REMINDER}/${id}/retry`, { method: "POST", auth: true }),
};
