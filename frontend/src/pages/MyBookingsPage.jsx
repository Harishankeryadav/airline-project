import { useState } from "react";
import { RefreshCw } from "lucide-react";
import { EmptyState, ErrorNote, Spinner, StatusBadge } from "../components/ui.jsx";
import { useAuth } from "../lib/auth.jsx";
import { bookingApi } from "../lib/endpoints.js";
import { formatDateTime, formatPrice, isPast } from "../lib/format.js";
import { useAction } from "../lib/useAction.js";
import { useAsync } from "../lib/useAsync.js";

export function canCancel(booking) {
  return booking.status === "CONFIRMED" && !isPast(booking.departureTime);
}

/** One booking with a two-step cancel (so a stray click cannot cancel a trip). Also used by the admin booking list. */
export function BookingCard({ booking, onChanged, showOwner = false }) {
  const [confirming, setConfirming] = useState(false);
  const cancel = useAction(async () => {
    const updated = await bookingApi.cancel(booking.id);
    setConfirming(false);
    onChanged(updated);
  });

  return (
    <li className="booking">
      <div className="booking-main">
        <div>
          <span className="mono flight-no">{booking.flightNumber}</span>
          <span className="booking-route">{booking.origin} to {booking.destination}</span>
        </div>
        <div className="sub">
          Departs {formatDateTime(booking.departureTime)}. {booking.noOfSeats} {booking.noOfSeats === 1 ? "seat" : "seats"}.
          Booking #{booking.id}{showOwner ? `, user #${booking.userId}` : ""}.
        </div>
        {booking.status === "FAILED" && booking.failureReason && (
          <div className="sub tone-bad">Not completed: {booking.failureReason}</div>
        )}
      </div>

      <div className="booking-side">
        <span className="mono fare">{formatPrice(booking.totalCost)}</span>
        <StatusBadge status={booking.status} />
        {canCancel(booking) && !confirming && (
          <button type="button" className="btn btn-ghost" onClick={() => setConfirming(true)}>Cancel booking</button>
        )}
      </div>

      {confirming && (
        <div className="booking-confirm" role="alertdialog" aria-label={`Cancel booking ${booking.id}`}>
          <span>Cancel booking #{booking.id}? The seats go back on sale.</span>
          <button type="button" className="btn btn-danger" disabled={cancel.loading} onClick={() => cancel.run()}>
            {cancel.loading ? "Cancelling..." : "Yes, cancel booking"}
          </button>
          <button type="button" className="btn btn-ghost" onClick={() => setConfirming(false)}>Keep booking</button>
        </div>
      )}
      <ErrorNote message={cancel.error} />
    </li>
  );
}

export default function MyBookingsPage({ navigate }) {
  const { user } = useAuth();
  const bookings = useAsync(() => (user ? bookingApi.mine() : Promise.resolve(null)), [user?.id]);

  if (!user) {
    return (
      <EmptyState title="Sign in to see your bookings"
                  action={<button className="btn btn-primary" onClick={() => navigate("account")}>Sign in</button>} />
    );
  }

  function replace(updated) {
    bookings.setData(bookings.data.map((b) => (b.id === updated.id ? updated : b)));
  }

  return (
    <div>
      <div className="page-head">
        <h1 className="page-title">My bookings</h1>
        <button className="btn btn-ghost" onClick={bookings.reload} disabled={bookings.loading}>
          <RefreshCw size={14} aria-hidden="true" /> Refresh
        </button>
      </div>

      <ErrorNote message={bookings.error} />
      {bookings.loading && !bookings.data && <Spinner label="Loading your bookings" />}

      {bookings.data && bookings.data.length === 0 && (
        <EmptyState title="No bookings yet"
                    action={<button className="btn btn-primary" onClick={() => navigate("search")}>Find a flight</button>}>
          Your confirmed trips will be listed here.
        </EmptyState>
      )}

      {bookings.data && bookings.data.length > 0 && (
        <ul className="booking-list">
          {bookings.data.map((b) => <BookingCard key={b.id} booking={b} onChanged={replace} />)}
        </ul>
      )}
    </div>
  );
}
