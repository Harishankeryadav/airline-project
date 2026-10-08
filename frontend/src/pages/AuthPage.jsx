import { useState } from "react";
import { Loader2, LogOut } from "lucide-react";
import { ErrorNote, Field } from "../components/ui.jsx";
import { useAuth } from "../lib/auth.jsx";
import { sentenceCase } from "../lib/format.js";
import { useAction } from "../lib/useAction.js";

export default function AuthPage({ navigate }) {
  const { user, notice, clearNotice, signIn, signUp, signOut } = useAuth();
  const [mode, setMode] = useState("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const submit = useAction(async () => {
    if (mode === "signup") await signUp(email.trim(), password);
    else await signIn(email.trim(), password);
    navigate("search");
  });

  if (user) {
    const endsAt = new Date(user.exp * 1000).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
    return (
      <div className="panel narrow">
        <h1 className="page-title">Your account</h1>
        <dl className="facts">
          <dt>Email</dt><dd className="mono">{user.email}</dd>
          <dt>Access</dt><dd>{user.roles.length ? user.roles.map(sentenceCase).join(", ") : "None"}</dd>
          <dt>Signed in until</dt><dd className="mono">{endsAt}</dd>
        </dl>
        <button className="btn btn-ghost" onClick={signOut}><LogOut size={14} aria-hidden="true" /> Sign out</button>
      </div>
    );
  }

  return (
    <div className="panel narrow">
      <h1 className="page-title">{mode === "signin" ? "Sign in" : "Create an account"}</h1>

      {notice && (
        <div className="note note-warn" role="status">
          <span>{notice}</span>
          <button type="button" className="link-btn" onClick={clearNotice}>Dismiss</button>
        </div>
      )}

      <div className="segmented" role="tablist" aria-label="Account">
        <button role="tab" aria-selected={mode === "signin"} onClick={() => setMode("signin")}>Sign in</button>
        <button role="tab" aria-selected={mode === "signup"} onClick={() => setMode("signup")}>Create account</button>
      </div>

      <form onSubmit={(e) => { e.preventDefault(); submit.run(); }}>
        <Field label="Email">
          <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
                 placeholder="you@example.com" />
        </Field>
        <Field label="Password" hint={mode === "signup" ? "8 to 72 characters." : undefined}>
          <input type="password" required minLength={mode === "signup" ? 8 : undefined} maxLength={72}
                 autoComplete={mode === "signup" ? "new-password" : "current-password"}
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <button className="btn btn-primary wide" disabled={submit.loading}>
          {submit.loading && <Loader2 size={15} className="spin" aria-hidden="true" />}
          {mode === "signin" ? "Sign in" : "Create account"}
        </button>
      </form>
      <ErrorNote message={submit.error} />
      {mode === "signup" && (
        <p className="panel-desc">New accounts are customer accounts. An administrator can grant more access later.</p>
      )}
    </div>
  );
}
