/**
 * Platform roles in ascending privilege order (SECURITY.md §3.1).
 * Frontend role checks are UX gating ONLY — the backend is the sole authority.
 */
export const ROLES = ['LEARNER', 'AUTHOR', 'CURATOR', 'ADMIN'] as const

export type Role = (typeof ROLES)[number]

/** True when any granted role is at or above the required one in the hierarchy. */
export function hasRole(granted: readonly string[], required: Role): boolean {
  const requiredRank = ROLES.indexOf(required)
  return granted.some((role) => {
    const rank = ROLES.indexOf(role as Role)
    return rank >= requiredRank && rank !== -1
  })
}
