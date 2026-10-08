import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { configureApi } from "./api.js";
import { authApi } from "./endpoints.js";
import { userFromToken } from "./jwt.js";

const TOKEN_KEY = "airline.token";
const AuthContext = createContext(null);

// sessionStorage: survives a page refresh, is gone when the tab closes. The token itself lasts 60 minutes.
function loadToken() {
  try {
    const stored = sessionStorage.getItem(TOKEN_KEY);
    if (stored && userFromToken(stored)) return stored;
    sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable */
  }
  return null;
}

function storeToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(loadToken);
  const [notice, setNotice] = useState(null);
  const user = useMemo(() => userFromToken(token), [token]);

  const tokenRef = useRef(token);
  tokenRef.current = token;

  const signOut = useCallback((message = null) => {
    storeToken(null);
    setToken(null);
    setNotice(message);
  }, []);

  // Configured while rendering (not in an effect) so child components' first requests already use it.
  configureApi({
    getToken: () => tokenRef.current,
    onUnauthorized: () => signOut("Your session has ended. Sign in again."),
  });

  // Sign out by itself when the token expires.
  useEffect(() => {
    if (!user) return undefined;
    const msLeft = Math.min(user.exp * 1000 - Date.now(), 2 ** 31 - 1);
    const timer = setTimeout(() => signOut("Your session has ended. Sign in again."), Math.max(msLeft, 0));
    return () => clearTimeout(timer);
  }, [user, signOut]);

  const signIn = useCallback(async (email, password) => {
    const result = await authApi.signin(email, password);
    storeToken(result.token);
    setToken(result.token);
    setNotice(null);
    return result;
  }, []);

  const signUp = useCallback(
    async (email, password) => {
      await authApi.signup(email, password);
      return signIn(email, password); // the account exists now - no need to make the person type it all again
    },
    [signIn]
  );

  const value = useMemo(
    () => ({
      user,
      notice,
      clearNotice: () => setNotice(null),
      signIn,
      signUp,
      signOut: () => signOut(null),
      isAdmin: !!user?.roles.includes("ADMIN"),
      canManageFlights: !!user?.roles.some((r) => r === "ADMIN" || r === "AIRLINE_BUSINESS"),
    }),
    [user, notice, signIn, signUp, signOut]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside <AuthProvider>");
  return value;
}
