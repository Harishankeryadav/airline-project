import { X } from "lucide-react";
import { useState } from "react";
import { EmptyState, ErrorNote, Spinner } from "../../components/ui.jsx";
import { authApi } from "../../lib/endpoints.js";
import { sentenceCase } from "../../lib/format.js";
import { useAction } from "../../lib/useAction.js";
import { useAsync } from "../../lib/useAsync.js";

const ROLES = ["ADMIN", "AIRLINE_BUSINESS", "CUSTOMER"];

/** Who has which role (ADMIN). A role change reaches the user's token the next time they sign in. */
export default function UsersAdmin() {
  const users = useAsync(() => authApi.listUsers(), []);

  function replace(updated) {
    users.setData(users.data.map((u) => (u.id === updated.id ? updated : u)));
  }

  return (
    <div>
      <ErrorNote message={users.error} />
      {users.loading && !users.data && <Spinner label="Loading users" />}
      {users.data && users.data.length === 0 && <EmptyState title="No users yet" />}
      {users.data && users.data.length > 0 && (
        <ul className="booking-list">
          {users.data.map((u) => <UserRow key={u.id} user={u} onChanged={replace} />)}
        </ul>
      )}
    </div>
  );
}

function UserRow({ user, onChanged }) {
  const [role, setRole] = useState("");
  const grantable = ROLES.filter((r) => !user.roles.includes(r));

  const grant = useAction(async () => {
    onChanged(await authApi.grantRole(user.id, role));
    setRole("");
  });
  const revoke = useAction(async (name) => {
    onChanged(await authApi.revokeRole(user.id, name));
  });

  return (
    <li className="booking">
      <div className="booking-main">
        <div><span className="booking-route">{user.email}</span></div>
        <div className="chips">
          {user.roles.map((r) => (
            <span key={r} className="chip">
              {sentenceCase(r)}
              <button type="button" className="icon-btn" disabled={revoke.loading}
                      aria-label={`Remove ${sentenceCase(r)} from ${user.email}`} onClick={() => revoke.run(r)}>
                <X size={12} aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      </div>
      {grantable.length > 0 && (
        <div className="booking-side">
          <select aria-label={`Role to add for ${user.email}`} value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">Add a role</option>
            {grantable.map((r) => <option key={r} value={r}>{sentenceCase(r)}</option>)}
          </select>
          <button className="btn btn-ghost" disabled={!role || grant.loading} onClick={() => grant.run()}>Add role</button>
        </div>
      )}
      <ErrorNote message={grant.error || revoke.error} />
    </li>
  );
}
