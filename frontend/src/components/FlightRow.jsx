import { useState } from "react";
import { useAuth } from "../lib/auth.jsx";
import { bookingApi } from "../lib/endpoints.js";
import { useAction } from "../lib/useAction.js";
import {
  airportCode, formatDay, formatDuration, formatPrice, formatTime, isPast, seatStatus,
} from "../lib/format.js";
import { ErrorNote } from "./ui.jsx";

const MAX_SEATS_PER_BOOKING = 9; // same limit the booking-service enforces

/**
 * One line of the departure board, with the booking controls.
 *   onBooked(flightId) - lets the page reload this flight so the seat count is current
 */
export default function FlightRow({ flight, index = 0, defaultSeats = 1, navigate, onBooked }) {
  const { user } = useAuth();
  const [seats, setSeats] = useState(defaultSeats);
  const [booked, setBooked] = useState(null);

  const departed = isPast(flight.departureTime);
  const soldOut = flight.availableSeats <= 0;
  const seatInfo = seatStatus(flight.availableSeats);
  const maxSeats = Math.max(1, Math.min(MAX_SEATS_PER_BOOKING, flight.availableSeats));

  const book = useAction(async () => {
    const booking = await bookingApi.create({ flightId: flight.id, noOfSeats: Number(seats) });
    setBooked(booking);
    onBooked?.(flight.id);
  });

  function renderAction() {
    if (departed) return <span className="muted">Departed</span>;
    if (soldOut) return <span className="tone-bad">Sold out</span>;
    if (!user) {
      return (
        <button type="button" className="btn btn-ghost" onClick={() => navigate("account")}>
          Sign in to book
        </button>
      );
    }
    return (
      <div className="book-controls">
        <label className="seats-input">
          <span className="sr-only">{`Seats on ${flight.flightNumber}`}</span>
          <input
            type="number"
            min="1"
            max={maxSeats}
            value={seats}
            onChange={(e) => setSeats(e.target.value)}
            aria-label={`Seats on ${flight.flightNumber}`}
          />
        </label>
        <button type="button" className="btn btn-primary" disabled={book.loading} onClick={() => book.run()}>
          {book.loading ? "Booking..." : "Book"}
        </button>
      </div>
    );
  }

  return (
    <li className="board-row" style={{ "--i": index }}>
      <div className="cell-flight">
        <span className="mono flight-no">{flight.flightNumber}</span>
        <span className="sub">{flight.boardingGate ? `Gate ${flight.boardingGate}` : ""}</span>
      </div>

      <div className="cell-route">
        <span className="route-code">
          {airportCode(flight.departureAirport)}
          <span className="route-arrow" aria-label="to"> to </span>
          {airportCode(flight.arrivalAirport)}
        </span>
        <span className="sub">
          {flight.departureAirport.cityName} to {flight.arrivalAirport.cityName}
        </span>
      </div>

      <div className="cell-times">
        <span className="mono time-main">
          {formatTime(flight.departureTime)} <span className="muted">-</span> {formatTime(flight.arrivalTime)}
        </span>
        <span className="sub">
          {formatDay(flight.departureTime)}, {formatDuration(flight.departureTime, flight.arrivalTime)}
        </span>
      </div>

      <div className={`cell-seats tone-${seatInfo.tone}`}>{seatInfo.text}</div>
      <div className="cell-fare mono">{formatPrice(flight.price)}</div>
      <div className="cell-action">{renderAction()}</div>

      {booked && (
        <div className="row-confirm note note-ok" role="status">
          <span>
            Booking #{booked.id} is {booked.status.toLowerCase()}: {booked.noOfSeats}{" "}
            {booked.noOfSeats === 1 ? "seat" : "seats"}, {formatPrice(booked.totalCost)}. A confirmation email is on its way
            to {user?.email}.
          </span>
          <button type="button" className="link-btn" onClick={() => navigate("bookings")}>View my bookings</button>
        </div>
      )}
      {book.error && <div className="row-confirm"><ErrorNote message={book.error} /></div>}
    </li>
  );
}
