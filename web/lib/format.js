/** Pure formatting helpers. Never pass unescaped user strings into HTML templates. */
export function escape(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
}
export const clamp = (value, minimum = 0, maximum = 1) => Math.min(maximum, Math.max(minimum, Number.isFinite(Number(value)) ? Number(value) : minimum));
export const percent = value => `${(clamp(value) * 100).toFixed(1)}%`;
export const integer = value => Number(value ?? 0).toLocaleString('en-US', {maximumFractionDigits: 0});
export const decimal = value => Number(value ?? 0).toLocaleString('en-US', {maximumFractionDigits: 2});
export const shortId = id => String(id ?? '').slice(0, 8);
export function date(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : parsed.toLocaleString([], {month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'});
}
export function sortRows(rows, key, ascending = true) {
  return [...rows].sort((a,b) => {
    const left = a[key], right = b[key];
    const result = typeof left === 'number' && typeof right === 'number' ? left-right : String(left ?? '').localeCompare(String(right ?? ''));
    return ascending ? result : -result;
  });
}
export function uniqueBy(items, key) {
  return [...new Map(items.map(item => [item[key], item])).values()];
}
export const terminal = status => ['SUCCEEDED','FAILED','CANCELLED'].includes(status);
export const pretty = value => JSON.stringify(value, null, 2);
