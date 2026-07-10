/**
 * Extracts the flat `roles` claim from a JWT access token for UX gating only
 * (SECURITY.md §3.2.4) — the backend re-validates every request. The token is
 * decoded, never verified here, and never logged.
 */
export function decodeRolesFromAccessToken(token: string | undefined): string[] {
  if (!token) return []
  const payload = token.split('.')[1]
  if (!payload) return []
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims: unknown = JSON.parse(atob(base64))
    if (typeof claims !== 'object' || claims === null) return []
    const roles = (claims as Record<string, unknown>)['roles']
    if (!Array.isArray(roles)) return []
    return roles.filter((role): role is string => typeof role === 'string')
  } catch {
    return []
  }
}
