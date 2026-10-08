import { useState } from "react";
import { LogIn, LogOut, Plane, Search, Settings, ShieldCheck, Ticket } from "lucide-react";
import { AuthProvider, useAuth } from "./lib/auth.jsx";
import { DEFAULT_BASE_URL, getBaseUrl, setBaseUrl } from "./lib/api.js";
import { useHashRoute } from "./lib/useHashRoute.js";
import { sentenceCase } from "./lib/format.js";
import AdminPage from "./pages/AdminPage.jsx";
import AuthPage from "./pages/AuthPage.jsx";
import MyBookingsPage from "./pages/MyBookingsPage.jsx";
import SearchPage from "./pages/SearchPage.jsx";

export default function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  );
}

function Shell() {
  const { user, canManageFlights } = useAuth();
  const [route, navigate] = useHashRoute();

  const tabs = [
    { id: "search", label: "Flights", icon: Search },
    { id: "bookings", label: "My bookings", icon: Ticket },
    canManageFlights && { id: "admin", label: "Administration", icon: ShieldCheck },
    { id: "account", label: user ? "Account" : "Sign in", icon: user ? ShieldCheck : LogIn },
  ].filter(Boolean);

  // A person who loses access (signs out, or the session ends) must not be left on a page they can't use.
  const page = route === "admin" && !canManageFlights && user ? "search" : route;

  return (
    <div className="app">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <div className="brand">
          <Plane size={22} aria-hidden="true" />
          <div>
            <div className="brand-name">Airline</div>
            <div className="brand-sub">Flights and bookings</div>
          </div>
        </div>
        <TopBarRight />
      </header>

      <nav className="tabs" aria-label="Main">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button key={id} className="tab" aria-current={page === id ? "page" : undefined} onClick={() => navigate(id)}>
            <Icon size={15} aria-hidden="true" /> {label}
          </button>
        ))}
      </nav>

      <main id="main" className="content">
        {page === "search" && <SearchPage navigate={navigate} />}
        {page === "bookings" && <MyBookingsPage navigate={navigate} />}
        {page === "admin" && <AdminPage navigate={navigate} />}
        {page === "account" && <AuthPage navigate={navigate} />}
      </main>
    </div>
  );
}

function TopBarRight() {
  const { user, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const [url, setUrl] = useState(getBaseUrl());

  function save(e) {
    e.preventDefault();
    setBaseUrl(url);
    window.location.reload(); // simplest way to make every page start from a clean state against the new address
  }

  return (
    <div className="topbar-right">
      {user && (
        <div className="who">
          <span className="mono">{user.email}</span>
          <small>{user.roles.map(sentenceCase).join(", ")}</small>
        </div>
      )}
      {user && (
        <button className="btn btn-ghost" onClick={signOut}><LogOut size={14} aria-hidden="true" /> Sign out</button>
      )}
      <button className="icon-btn" aria-label="API gateway address" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Settings size={16} aria-hidden="true" />
      </button>
      {open && (
        <form className="popover" onSubmit={save}>
          <label className="field">
            <span className="field-label">API gateway address</span>
            <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder={DEFAULT_BASE_URL} />
          </label>
          <p className="field-hint">The default comes from VITE_API_BASE_URL. The page reloads after saving.</p>
          <button className="btn btn-primary">Save address</button>
        </form>
      )}
    </div>
  );
}
