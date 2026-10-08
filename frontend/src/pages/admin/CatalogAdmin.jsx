import { useState } from "react";
import AirportPicker from "../../components/AirportPicker.jsx";
import { Field, FormPanel } from "../../components/ui.jsx";
import { flightsApi } from "../../lib/endpoints.js";
import { localInputToUtcIso } from "../../lib/format.js";
import { useAction } from "../../lib/useAction.js";
import { useAsync } from "../../lib/useAsync.js";

/** Cities, airports, airplanes and flights (ADMIN or AIRLINE_BUSINESS). Dropdowns reload after each create. */
export default function CatalogAdmin() {
  const cities = useAsync(() => flightsApi.listCities(), []);
  const airplanes = useAsync(() => flightsApi.listAirplanes(), []);

  return (
    <div className="stack">
      <CityForm onCreated={cities.reload} />
      <AirportForm cities={cities.data ?? []} />
      <AirplaneForm onCreated={airplanes.reload} />
      <FlightForm airplanes={airplanes.data ?? []} />
    </div>
  );
}

function CityForm({ onCreated }) {
  const [name, setName] = useState("");
  const save = useAction(async () => {
    const city = await flightsApi.createCity(name.trim());
    setName("");
    onCreated();
    return `Added ${city.name}.`;
  });
  return (
    <FormPanel title="Add a city" onSubmit={(e) => { e.preventDefault(); save.run(); }}
               loading={save.loading} error={save.error} success={save.success} submitLabel="Add city">
      <Field label="City name">
        <input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} placeholder="Pune" />
      </Field>
    </FormPanel>
  );
}

function AirportForm({ cities }) {
  const [form, setForm] = useState({ name: "", code: "", address: "", cityId: "" });
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  const save = useAction(async () => {
    const airport = await flightsApi.createAirport({
      name: form.name.trim(),
      code: form.code.trim() || null,
      address: form.address.trim() || null,
      cityId: Number(form.cityId),
    });
    setForm({ name: "", code: "", address: "", cityId: "" });
    return `Added ${airport.name} in ${airport.cityName}.`;
  });
  return (
    <FormPanel title="Add an airport" description="Passengers find airports by name or city, so use the full official name."
               onSubmit={(e) => { e.preventDefault(); save.run(); }}
               loading={save.loading} error={save.error} success={save.success} submitLabel="Add airport">
      <div className="form-grid">
        <Field label="Airport name"><input required maxLength={150} value={form.name} onChange={set("name")} /></Field>
        <Field label="IATA code" hint="Three letters, optional">
          <input maxLength={3} pattern="[A-Za-z]{3}" title="Three letters, for example PNQ" value={form.code}
                 onChange={set("code")} placeholder="PNQ" />
        </Field>
        <Field label="City">
          <select required value={form.cityId} onChange={set("cityId")}>
            <option value="">Choose a city</option>
            {cities.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field label="Address"><input maxLength={255} value={form.address} onChange={set("address")} placeholder="Optional" /></Field>
      </div>
    </FormPanel>
  );
}

function AirplaneForm({ onCreated }) {
  const [form, setForm] = useState({ modelNumber: "", capacity: "" });
  const save = useAction(async () => {
    const plane = await flightsApi.createAirplane({
      modelNumber: form.modelNumber.trim(),
      capacity: form.capacity ? Number(form.capacity) : null,
    });
    setForm({ modelNumber: "", capacity: "" });
    onCreated();
    return `Added ${plane.modelNumber} with ${plane.capacity} seats.`;
  });
  return (
    <FormPanel title="Add an airplane" description="A flight starts with as many seats as its airplane has."
               onSubmit={(e) => { e.preventDefault(); save.run(); }}
               loading={save.loading} error={save.error} success={save.success} submitLabel="Add airplane">
      <div className="form-grid">
        <Field label="Model"><input required maxLength={100} value={form.modelNumber}
               onChange={(e) => setForm((f) => ({ ...f, modelNumber: e.target.value }))} placeholder="Airbus A321" /></Field>
        <Field label="Seats" hint="Defaults to 200 when empty">
          <input type="number" min="1" max="1000" value={form.capacity}
                 onChange={(e) => setForm((f) => ({ ...f, capacity: e.target.value }))} />
        </Field>
      </div>
    </FormPanel>
  );
}

function FlightForm({ airplanes }) {
  const empty = { flightNumber: "", airplaneId: "", departureTime: "", arrivalTime: "", price: "", boardingGate: "" };
  const [form, setForm] = useState(empty);
  const [from, setFrom] = useState(null);
  const [to, setTo] = useState(null);
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const save = useAction(async () => {
    if (!from || !to) throw new Error("Choose both a departure and an arrival airport.");
    const flight = await flightsApi.createFlight({
      flightNumber: form.flightNumber.trim(),
      airplaneId: Number(form.airplaneId),
      departureAirportId: from.id,
      arrivalAirportId: to.id,
      departureTime: localInputToUtcIso(form.departureTime),
      arrivalTime: localInputToUtcIso(form.arrivalTime),
      price: Number(form.price),
      boardingGate: form.boardingGate.trim() || null,
    });
    setForm(empty);
    setFrom(null);
    setTo(null);
    return `Added flight ${flight.flightNumber} with ${flight.totalSeats} seats.`;
  });

  return (
    <FormPanel title="Add a flight" description="Times are entered in your local time."
               onSubmit={(e) => { e.preventDefault(); save.run(); }}
               loading={save.loading} error={save.error} success={save.success} submitLabel="Add flight">
      <div className="form-grid">
        <Field label="Flight number"><input required maxLength={20} value={form.flightNumber} onChange={set("flightNumber")} placeholder="AI-202" /></Field>
        <Field label="Airplane">
          <select required value={form.airplaneId} onChange={set("airplaneId")}>
            <option value="">Choose an airplane</option>
            {airplanes.map((p) => <option key={p.id} value={p.id}>{p.modelNumber} ({p.capacity} seats)</option>)}
          </select>
        </Field>
        <AirportPicker label="From" value={from} onChange={setFrom} />
        <AirportPicker label="To" value={to} onChange={setTo} />
        <Field label="Departure"><input required type="datetime-local" value={form.departureTime} onChange={set("departureTime")} /></Field>
        <Field label="Arrival"><input required type="datetime-local" value={form.arrivalTime} onChange={set("arrivalTime")} /></Field>
        <Field label="Fare"><input required type="number" min="0.01" step="0.01" value={form.price} onChange={set("price")} /></Field>
        <Field label="Gate"><input maxLength={20} value={form.boardingGate} onChange={set("boardingGate")} placeholder="Optional" /></Field>
      </div>
    </FormPanel>
  );
}
