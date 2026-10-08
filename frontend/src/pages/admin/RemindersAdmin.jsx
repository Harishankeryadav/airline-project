import { RefreshCw } from "lucide-react";
import { useState } from "react";
import { EmptyState, ErrorNote, Field, FormPanel, Spinner, StatusBadge } from "../../components/ui.jsx";
import { reminderApi } from "../../lib/endpoints.js";
import { formatDateTime, localInputToUtcIso, sentenceCase } from "../../lib/format.js";
import { useAction } from "../../lib/useAction.js";
import { useAsync } from "../../lib/useAsync.js";

const STATUSES = ["PENDING", "SENDING", "SENT", "FAILED", "CANCELLED"];

/**
 * Emails sent by reminder-service (ADMIN). Booking confirmations, departure reminders and cancellation notices
 * are created automatically from booking events; this page shows them and lets you schedule extra ones.
 */
export default function RemindersAdmin() {
  const [status, setStatus] = useState("");
  const list = useAsync(() => reminderApi.list(status || undefined), [status]);

  return (
    <div className="stack">
      <ScheduleForm onCreated={list.reload} />

      <section>
        <div className="toolbar">
          <Field label="Status">
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All</option>
              {STATUSES.map((s) => <option key={s} value={s}>{sentenceCase(s)}</option>)}
            </select>
          </Field>
          <button className="btn btn-ghost" onClick={list.reload} disabled={list.loading}>
            <RefreshCw size={14} aria-hidden="true" /> Refresh
          </button>
        </div>
        <ErrorNote message={list.error} />
        {list.loading && !list.data && <Spinner label="Loading emails" />}
        {list.data && list.data.length === 0 && <EmptyState title="No emails with that status" />}
        {list.data && list.data.length > 0 && (
          <ul className="booking-list">
            {list.data.map((n) => <NotificationRow key={n.id} notification={n} onChanged={list.reload} />)}
          </ul>
        )}
      </section>
    </div>
  );
}

function NotificationRow({ notification: n, onChanged }) {
  const cancel = useAction(async () => { await reminderApi.cancel(n.id); onChanged(); });
  const retry = useAction(async () => { await reminderApi.retry(n.id); onChanged(); });
  return (
    <li className="booking">
      <div className="booking-main">
        <div><span className="booking-route">{n.subject}</span></div>
        <div className="sub">
          {sentenceCase(n.kind)} to {n.recipientEmail}. Due {formatDateTime(n.notificationTime)}.
          {n.bookingId ? ` Booking #${n.bookingId}.` : ""} Attempts: {n.attempts}.
        </div>
        {n.lastError && <div className="sub tone-bad">Last error: {n.lastError}</div>}
      </div>
      <div className="booking-side">
        <StatusBadge status={n.status} />
        {n.status === "PENDING" && (
          <button className="btn btn-ghost" disabled={cancel.loading} onClick={() => cancel.run()}>Cancel</button>
        )}
        {n.status === "FAILED" && (
          <button className="btn btn-ghost" disabled={retry.loading} onClick={() => retry.run()}>Retry</button>
        )}
      </div>
      <ErrorNote message={cancel.error || retry.error} />
    </li>
  );
}

function ScheduleForm({ onCreated }) {
  const empty = { subject: "", recipientEmail: "", notificationTime: "", content: "" };
  const [form, setForm] = useState(empty);
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  const save = useAction(async () => {
    const created = await reminderApi.create({
      subject: form.subject.trim(),
      content: form.content,
      recipientEmail: form.recipientEmail.trim(),
      notificationTime: form.notificationTime ? localInputToUtcIso(form.notificationTime) : null,
    });
    setForm(empty);
    onCreated();
    return `Email #${created.id} is scheduled.`;
  });
  return (
    <FormPanel title="Schedule an email" description="Leave the time empty to send it right away."
               onSubmit={(e) => { e.preventDefault(); save.run(); }}
               loading={save.loading} error={save.error} success={save.success} submitLabel="Schedule email">
      <div className="form-grid">
        <Field label="Subject"><input required maxLength={200} value={form.subject} onChange={set("subject")} /></Field>
        <Field label="Recipient"><input required type="email" value={form.recipientEmail} onChange={set("recipientEmail")} /></Field>
        <Field label="Send at"><input type="datetime-local" value={form.notificationTime} onChange={set("notificationTime")} /></Field>
      </div>
      <Field label="Message"><textarea required rows={3} value={form.content} onChange={set("content")} /></Field>
    </FormPanel>
  );
}
