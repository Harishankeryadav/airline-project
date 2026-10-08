import { useCallback, useEffect, useRef, useState } from "react";
import { Search } from "lucide-react";
import AirportPicker from "../components/AirportPicker.jsx";
import FlightRow from "../components/FlightRow.jsx";
import { EmptyState, ErrorNote, Field, Spinner } from "../components/ui.jsx";
import { flightsApi } from "../lib/endpoints.js";

const SORTS = {
  earliest: { sortBy: "departureTime", order: "asc", label: "Earliest departure" },
  cheapest: { sortBy: "price", order: "asc", label: "Lowest fare" },
};

export default function SearchPage({ navigate }) {
  const [from, setFrom] = useState(null);
  const [to, setTo] = useState(null);
  const [date, setDate] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [passengers, setPassengers] = useState(1);
  const [sort, setSort] = useState("earliest");

  const [board, setBoard] = useState({ flights: null, loading: true, error: null });
  const [searched, setSearched] = useState(false);
  const latest = useRef(0);

  const runSearch = useCallback(async (filters) => {
    const id = ++latest.current;
    setBoard((b) => ({ ...b, loading: true, error: null }));
    try {
      const flights = await flightsApi.searchFlights(filters);
      if (id === latest.current) setBoard({ flights, loading: false, error: null });
    } catch (e) {
      if (id === latest.current) setBoard({ flights: null, loading: false, error: e.message });
    }
  }, []);

  // The board is never empty on arrival: show the next departures until the person narrows it down.
  useEffect(() => {
    runSearch({ sortBy: "departureTime", order: "asc", size: 20 });
  }, [runSearch]);

  function filters() {
    const { sortBy, order } = SORTS[sort];
    return {
      departureAirportId: from?.id,
      arrivalAirportId: to?.id,
      departureDate: date,        // the backend matches this as a UTC calendar day
      maxPrice,
      seats: passengers > 1 ? passengers : "",
      sortBy,
      order,
      size: 50,
    };
  }

  function onSubmit(e) {
    e.preventDefault();
    setSearched(true);
    runSearch(filters());
  }

  // After a booking, fetch that flight again so its seat count is current.
  async function refreshFlight(flightId) {
    try {
      const fresh = await flightsApi.getFlight(flightId);
      setBoard((b) => (b.flights ? { ...b, flights: b.flights.map((f) => (f.id === flightId ? fresh : f)) } : b));
    } catch {
      /* the booking itself succeeded; a stale seat count is harmless */
    }
  }

  return (
    <div>
      <h1 className="page-title">Find a flight</h1>

      <form className="panel search-form" onSubmit={onSubmit}>
        <div className="form-grid">
          <AirportPicker label="From" value={from} onChange={setFrom} />
          <AirportPicker label="To" value={to} onChange={setTo} />
          <Field label="Date" hint="Matched on UTC dates">
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
          <Field label="Passengers">
            <input type="number" min="1" max="9" value={passengers}
                   onChange={(e) => setPassengers(Math.max(1, Math.min(9, Number(e.target.value) || 1)))} />
          </Field>
          <Field label="Highest fare">
            <input type="number" min="0" inputMode="decimal" value={maxPrice} placeholder="No limit"
                   onChange={(e) => setMaxPrice(e.target.value)} />
          </Field>
          <Field label="Sort by">
            <select value={sort} onChange={(e) => setSort(e.target.value)}>
              {Object.entries(SORTS).map(([key, s]) => <option key={key} value={key}>{s.label}</option>)}
            </select>
          </Field>
        </div>
        <button className="btn btn-primary" disabled={board.loading}>
          <Search size={15} aria-hidden="true" /> {board.loading ? "Searching..." : "Search flights"}
        </button>
      </form>

      <ErrorNote message={board.error} />

      <section aria-live="polite" aria-busy={board.loading}>
        {board.loading && !board.flights && <Spinner label="Loading flights" />}

        {board.flights && board.flights.length === 0 && (
          <EmptyState title={searched ? "No flights match those filters" : "No flights scheduled yet"}>
            {searched
              ? "Try another date, remove the fare limit, or search for a different city."
              : "Flights appear here once an administrator adds them."}
          </EmptyState>
        )}

        {board.flights && board.flights.length > 0 && (
          <>
            <h2 className="board-title">{searched ? "Matching flights" : "Next departures"}</h2>
            <div className="board-head" aria-hidden="true">
              <span>Flight</span><span>Route</span><span>Departs and arrives</span><span>Seats</span><span>Fare</span><span />
            </div>
            <ol className="board" key={board.flights.map((f) => f.id).join("-")}>
              {board.flights.map((flight, i) => (
                <FlightRow key={flight.id} flight={flight} index={i} defaultSeats={passengers}
                           navigate={navigate} onBooked={refreshFlight} />
              ))}
            </ol>
          </>
        )}
      </section>
    </div>
  );
}
