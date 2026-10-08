import { useState } from "react";
import { EmptyState } from "../components/ui.jsx";
import { useAuth } from "../lib/auth.jsx";
import BookingsAdmin from "./admin/BookingsAdmin.jsx";
import CatalogAdmin from "./admin/CatalogAdmin.jsx";
import RemindersAdmin from "./admin/RemindersAdmin.jsx";
import UsersAdmin from "./admin/UsersAdmin.jsx";

/**
 * Which sections a person sees follows the gateway's rules (docs/ARCHITECTURE.md section 3):
 * flights and airports: ADMIN or AIRLINE_BUSINESS; bookings, emails and users: ADMIN only.
 * Hiding a section is a convenience - the gateway and services refuse the calls anyway.
 */
export default function AdminPage({ navigate }) {
  const { user, isAdmin, canManageFlights } = useAuth();

  const sections = [
    canManageFlights && { id: "catalog", label: "Flights and airports", Component: CatalogAdmin },
    isAdmin && { id: "bookings", label: "All bookings", Component: BookingsAdmin },
    isAdmin && { id: "emails", label: "Emails", Component: RemindersAdmin },
    isAdmin && { id: "users", label: "Users", Component: UsersAdmin },
  ].filter(Boolean);

  const [selected, setSelected] = useState(null);

  if (!user) {
    return (
      <EmptyState title="Sign in to manage flights"
                  action={<button className="btn btn-primary" onClick={() => navigate("account")}>Sign in</button>} />
    );
  }
  if (sections.length === 0) {
    return (
      <EmptyState title="You don't have access to this area">
        Administration needs an administrator or airline business account.
      </EmptyState>
    );
  }

  const current = sections.find((s) => s.id === selected) ?? sections[0];
  const Section = current.Component;

  return (
    <div>
      <h1 className="page-title">Administration</h1>
      <div className="segmented" role="tablist" aria-label="Administration sections">
        {sections.map((s) => (
          <button key={s.id} role="tab" aria-selected={s.id === current.id} onClick={() => setSelected(s.id)}>
            {s.label}
          </button>
        ))}
      </div>
      <Section />
    </div>
  );
}
