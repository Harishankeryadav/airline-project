// One place that talks HTTP. Everything goes through the api-gateway; paths are /<service>/<service path>.
// Every backend reply is the envelope { success, message, data, err }.

const STORAGE_KEY = "airline.apiBaseUrl";

export const DEFAULT_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

let baseUrl = readStoredBaseUrl();
let getToken = () => null;
let onUnauthorized = () => {};

function readStoredBaseUrl() {
  try {
    return localStorage.getItem(STORAGE_KEY) || DEFAULT_BASE_URL;
  } catch {
    return DEFAULT_BASE_URL;
  }
}

export function getBaseUrl() {
  return baseUrl;
}

export function setBaseUrl(url) {
  baseUrl = (url || DEFAULT_BASE_URL).trim().replace(/\/+$/, "");
  try {
    localStorage.setItem(STORAGE_KEY, baseUrl);
  } catch {
    /* storage unavailable - the setting just lasts for this page view */
  }
}

/** Called once by AuthProvider: how to read the current token, and what to do when the server says it is no good. */
export function configureApi(options) {
  getToken = options.getToken ?? getToken;
  onUnauthorized = options.onUnauthorized ?? onUnauthorized;
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const STATUS_MESSAGES = {
  401: "Please sign in to continue.",
  403: "You don't have permission to do that.",
  404: "That could not be found.",
  429: "Too many requests. Wait a few seconds and try again.",
  503: "A service is temporarily unavailable. Try again in a moment.",
  504: "The service took too long to respond. Try again in a moment.",
};

/**
 * Picks the most useful sentence from an error envelope:
 *   {message:"Conflict", err:"Only 2 seat(s) left on flight AI-101, requested 5"}  -> the err text
 *   {message:"Validation failed", err:{email:"must be a valid email"}}              -> "email: must be a valid email"
 */
export function messageFromEnvelope(data, status) {
  if (data && typeof data === "object") {
    const { err, message } = data;
    if (typeof err === "string" && err.trim()) return err;
    if (err && typeof err === "object" && Object.keys(err).length > 0) {
      return Object.entries(err).map(([field, text]) => `${field}: ${text}`).join("; ");
    }
    if (typeof message === "string" && message.trim()) return message;
  }
  return STATUS_MESSAGES[status] ?? `The request failed (status ${status}).`;
}

/**
 * @param {string} path     e.g. "/flightsservice/api/v1/flights"
 * @param {{method?:string, body?:object, auth?:boolean, signal?:AbortSignal}} options
 * @returns the envelope's `data`
 * @throws {ApiError} with a message that is safe to show to the user
 */
export async function request(path, { method = "GET", body, auth = false, signal } = {}) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = auth ? getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (e) {
    if (e?.name === "AbortError") throw e; // a cancelled search is not an error to show
    throw new ApiError(
      `Cannot reach the API gateway at ${baseUrl}. Check that it is running and that this page's address is in its CORS_ALLOWED_ORIGINS.`,
      0
    );
  }

  let data = null;
  try {
    data = await response.json();
  } catch {
    /* an empty or non-JSON body (for example a 429 from the rate limiter) */
  }

  if (!response.ok || (data && data.success === false)) {
    if (response.status === 401 && token) {
      onUnauthorized(); // the token we sent was rejected: it expired or is no longer valid
      throw new ApiError("Your session has ended. Sign in again.", 401);
    }
    throw new ApiError(messageFromEnvelope(data, response.status), response.status);
  }
  return data ? data.data : null;
}
