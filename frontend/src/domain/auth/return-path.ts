/** Only supported internal detail routes may survive an authentication redirect. */
export function safeReturnPath(value: unknown): string {
  return typeof value === 'string' &&
    /^\/pills\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    ? value
    : '/'
}
