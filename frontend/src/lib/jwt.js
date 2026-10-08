// The token is only DECODED here to show who is signed in and which tabs to offer.
// It is not trusted for security: the gateway and every service verify the signature themselves.

function base64UrlDecode(part) {
  const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
  const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

export function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];
    return payload ? JSON.parse(base64UrlDecode(payload)) : null;
  } catch {
    return null;
  }
}

/**
 * auth-service token claims: sub = user id, email, roles = ["ADMIN", ...], exp (seconds).
 * @returns {{id:number,email:string,roles:string[],exp:number}|null} null if malformed or already expired
 */
export function userFromToken(token, now = Date.now()) {
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims || !claims.sub || !claims.email || typeof claims.exp !== "number") return null;
  if (claims.exp * 1000 <= now) return null;
  return {
    id: Number(claims.sub),
    email: claims.email,
    roles: Array.isArray(claims.roles) ? claims.roles.map(String) : [],
    exp: claims.exp,
  };
}
