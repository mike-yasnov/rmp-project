export function formatMoney(value: number, currency: string = '₽'): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? '−' : '';
  const whole = Math.floor(abs);
  const frac = Math.round((abs - whole) * 100);
  const wholeStr = whole
    .toString()
    .split('')
    .reverse()
    .join('')
    .replace(/(\d{3})(?=\d)/g, '$1 ')
    .split('')
    .reverse()
    .join('');
  return `${sign}${wholeStr},${frac.toString().padStart(2, '0')} ${currency}`;
}

export function formatNumber(value: number, decimals: number = 2): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? '−' : '';
  const m = Math.pow(10, decimals);
  const rounded = Math.round(abs * m) / m;
  const whole = Math.floor(rounded);
  const frac = Math.round((rounded - whole) * m);
  const wholeStr = whole
    .toString()
    .split('')
    .reverse()
    .join('')
    .replace(/(\d{3})(?=\d)/g, '$1 ')
    .split('')
    .reverse()
    .join('');
  return `${sign}${wholeStr},${frac.toString().padStart(decimals, '0')}`;
}
