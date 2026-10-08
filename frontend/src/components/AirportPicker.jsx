import { useEffect, useId, useRef, useState } from "react";
import { X } from "lucide-react";
import { flightsApi } from "../lib/endpoints.js";
import { airportCode } from "../lib/format.js";

const MIN_CHARS = 2;
const DEBOUNCE_MS = 250;

/**
 * Type a city or an airport name; suggestions come from GET /airports/search (partial, case-insensitive,
 * results that start with the text first). Pick one with the mouse or Up/Down + Enter.
 *   value    - the chosen airport object ({id, name, code, cityName, ...}) or null
 *   onChange - called with the airport, or null when cleared
 */
export default function AirportPicker({ label, value, onChange, placeholder = "City or airport" }) {
  const baseId = useId();
  const inputId = `${baseId}-input`;
  const listId = `${baseId}-list`;

  const [query, setQuery] = useState("");
  const [options, setOptions] = useState([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [status, setStatus] = useState("");
  const inputRef = useRef(null);

  useEffect(() => {
    if (value) return undefined;
    const text = query.trim();
    if (text.length < MIN_CHARS) {
      setOptions([]);
      setOpen(false);
      setStatus("");
      return undefined;
    }
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const found = await flightsApi.searchAirports(text, 8, controller.signal);
        setOptions(found);
        setActive(0);
        setOpen(true);
        setStatus(found.length === 0 ? "No airports match that text." : "");
      } catch (e) {
        if (e.name !== "AbortError") {
          setOptions([]);
          setOpen(false);
          setStatus(e.message);
        }
      }
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort(); // a slower, older search must never overwrite a newer one
    };
  }, [query, value]);

  function choose(airport) {
    onChange(airport);
    setQuery("");
    setOptions([]);
    setOpen(false);
    setStatus("");
  }

  function clear() {
    onChange(null);
    setTimeout(() => inputRef.current?.focus(), 0);
  }

  function onKeyDown(e) {
    if (e.key === "ArrowDown" && options.length > 0) {
      e.preventDefault();
      setOpen(true);
      setActive((i) => (i + 1) % options.length);
    } else if (e.key === "ArrowUp" && options.length > 0) {
      e.preventDefault();
      setActive((i) => (i - 1 + options.length) % options.length);
    } else if (e.key === "Enter" && open && options[active]) {
      e.preventDefault(); // pick the suggestion instead of submitting the surrounding form
      choose(options[active]);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  }

  if (value) {
    return (
      <div className="field">
        <span className="field-label">{label}</span>
        <div className="picked">
          <span className="code">{airportCode(value)}</span>
          <span className="picked-name">
            {value.cityName}
            <small>{value.name}</small>
          </span>
          <button type="button" className="icon-btn" onClick={clear} aria-label={`Clear ${label}`}>
            <X size={14} aria-hidden="true" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="field picker">
      <label className="field-label" htmlFor={inputId}>{label}</label>
      <input
        ref={inputRef}
        id={inputId}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && options[active] ? `${baseId}-opt-${active}` : undefined}
        autoComplete="off"
        value={query}
        placeholder={placeholder}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => setOpen(false)}
        onFocus={() => options.length > 0 && setOpen(true)}
      />
      {open && options.length > 0 && (
        <ul id={listId} role="listbox" className="suggestions" aria-label={`${label} suggestions`}>
          {options.map((airport, index) => (
            <li
              key={airport.id}
              id={`${baseId}-opt-${index}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? "is-active" : ""}
              onMouseDown={(e) => {
                e.preventDefault(); // keep focus in the input so onBlur doesn't close the list before the click lands
                choose(airport);
              }}
              onMouseEnter={() => setActive(index)}
            >
              <span className="code">{airportCode(airport)}</span>
              <span className="picked-name">
                {airport.cityName}
                <small>{airport.name}</small>
              </span>
            </li>
          ))}
        </ul>
      )}
      {status && <span className="field-hint" role="status">{status}</span>}
    </div>
  );
}
