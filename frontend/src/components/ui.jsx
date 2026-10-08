import { cloneElement, isValidElement, useId } from "react";
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { sentenceCase, statusTone } from "../lib/format.js";

export function ErrorNote({ message }) {
  if (!message) return null;
  return (
    <div className="note note-bad" role="alert">
      <AlertCircle size={16} aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

export function SuccessNote({ message }) {
  if (!message) return null;
  return (
    <div className="note note-ok" role="status">
      <CheckCircle2 size={16} aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

export function Spinner({ label = "Loading" }) {
  return (
    <span className="spinner" role="status">
      <Loader2 size={16} className="spin" aria-hidden="true" />
      <span>{label}</span>
    </span>
  );
}

/**
 * A label above one form control. The label is linked to the control (clicking the text focuses it, screen readers
 * announce it) and the hint is linked as its DESCRIPTION, so it does not become part of the control's name.
 */
export function Field({ label, hint, children }) {
  const id = useId();
  const hintId = `${id}-hint`;
  const control = isValidElement(children)
    ? cloneElement(children, { id, "aria-describedby": hint ? hintId : undefined })
    : children;
  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>{label}</label>
      {control}
      {hint && <span id={hintId} className="field-hint">{hint}</span>}
    </div>
  );
}

export function StatusBadge({ status }) {
  return <span className={`badge badge-${statusTone(status)}`}>{sentenceCase(status)}</span>;
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="empty">
      <div className="empty-title">{title}</div>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

/** A titled form card used by every admin form. */
export function FormPanel({ title, description, onSubmit, loading, error, success, submitLabel, children }) {
  return (
    <section className="panel">
      <h3 className="panel-title">{title}</h3>
      {description && <p className="panel-desc">{description}</p>}
      <form onSubmit={onSubmit}>
        {children}
        <button className="btn btn-primary" disabled={loading}>
          {loading ? "Saving..." : submitLabel}
        </button>
      </form>
      <ErrorNote message={error} />
      <SuccessNote message={success} />
    </section>
  );
}
