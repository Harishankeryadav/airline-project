import { RefreshCw } from "lucide-react";
import { useState } from "react";
import { BookingCard } from "../MyBookingsPage.jsx";
import { EmptyState, ErrorNote, Field, Spinner } from "../../components/ui.jsx";
import { bookingApi } from "../../lib/endpoints.js";
import { useAsync } from "../../lib/useAsync.js";

const STATUSES = ["CONFIRMED", "CANCELLED", "FAILED", "PENDING"];

/** Everyone's bookings (ADMIN). */
export default function BookingsAdmin() {
  const [status, setStatus] = useState("");
  const bookings = useAsync(() => bookingApi.all(status || undefined), [status]);

  function replace(updated) {
    bookings.setData(bookings.data.map((b) => (b.id === updated.id ? updated : b)));
  }

  return (
    <div>
      <div className="toolbar">
        <Field label="Status">
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
          </select>
        </Field>
        <button className="btn btn-ghost" onClick={bookings.reload} disabled={bookings.loading}>
          <RefreshCw size={14} aria-hidden="true" /> Refresh
        </button>
      </div>
      <ErrorNote message={bookings.error} />
      {bookings.loading && !bookings.data && <Spinner label="Loading bookings" />}
      {bookings.data && bookings.data.length === 0 && <EmptyState title="No bookings with that status" />}
      {bookings.data && bookings.data.length > 0 && (
        <ul className="booking-list">
          {bookings.data.map((b) => <BookingCard key={b.id} booking={b} onChanged={replace} showOwner />)}
        </ul>
      )}
    </div>
  );
}
