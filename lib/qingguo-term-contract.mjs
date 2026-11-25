/** Offline shared response-shape check. Never proves account identity. */
export const REQUIRED_TERM_FIELDS = Object.freeze([
  'xn', 'xqM', 'xqName', 'jwUserYeartermId', 'userCode'
]);

// Match Python str.strip's Unicode whitespace set, including C0 separators.
const nonblank = /[^\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]/u;

export function validTermShape(payload) {
  return payload !== null && typeof payload === 'object' && !Array.isArray(payload)
    && REQUIRED_TERM_FIELDS.every(key =>
      Object.hasOwn(payload, key) && typeof payload[key] === 'string'
      && nonblank.test(payload[key]) && [...payload[key]].length <= 100);
}
