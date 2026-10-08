import { useCallback, useEffect, useState } from "react";

export const ROUTES = ["search", "bookings", "admin", "account"];

function readRoute() {
  const hash = window.location.hash.replace(/^#\/?/, "");
  return ROUTES.includes(hash) ? hash : "search";
}

/** Tiny hash router (#/search, #/bookings, ...) so the browser's back button and reloads keep working. */
export function useHashRoute() {
  const [route, setRoute] = useState(readRoute);

  useEffect(() => {
    const onChange = () => setRoute(readRoute());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);

  const navigate = useCallback((next) => {
    window.location.hash = `/${next}`;
  }, []);

  return [route, navigate];
}
