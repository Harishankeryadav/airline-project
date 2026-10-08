const CURRENCY = import.meta.env.VITE_CURRENCY || "INR";

export function formatPrice(value, currency = CURRENCY) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return "-";
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency, maximumFractionDigits: 2 }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

export function formatDateTime(iso) {
  const d = new Date(iso);
  if (!iso || Number.isNaN(d.getTime())) return "-";
  return d.toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}

export function formatTime(iso) {
  const d = new Date(iso);
  if (!iso || Number.isNaN(d.getTime())) return "-";
  return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

export function formatDay(iso) {
  const d = new Date(iso);
  if (!iso || Number.isNaN(d.getTime())) return "";
  return d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

export function formatDuration(startIso, endIso) {
  const minutes = Math.round((new Date(endIso) - new Date(startIso)) / 60000);
  if (!Number.isFinite(minutes) || minutes <= 0) return "-";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}h ${String(m).padStart(2, "0")}m` : `${m}m`;
}

/** The short code shown on the board: IATA code if the airport has one, otherwise the city name. */
export function airportCode(airport) {
  return airport?.code || airport?.cityName || "-";
}

export function isPast(iso, now = Date.now()) {
  return new Date(iso).getTime() <= now;
}

/** How many seats are left, as a short phrase plus a tone for colouring. */
export function seatStatus(availableSeats) {
  if (availableSeats <= 0) return { text: "Sold out", tone: "bad" };
  if (availableSeats <= 5) return { text: `${availableSeats} left`, tone: "warn" };
  return { text: `${availableSeats} seats`, tone: "ok" };
}

const STATUS_TONES = {
  CONFIRMED: "ok",
  SENT: "ok",
  PENDING: "warn",
  SENDING: "warn",
  CANCELLED: "muted",
  FAILED: "bad",
};

export function statusTone(status) {
  return STATUS_TONES[status] ?? "muted";
}

/** "<input type=datetime-local>" value (local time, no zone) -> ISO-8601 in UTC, which is what the backend expects. */
export function localInputToUtcIso(value) {
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

export function sentenceCase(text) {
  if (!text) return "";
  const lower = String(text).toLowerCase().replace(/_/g, " ");
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}
