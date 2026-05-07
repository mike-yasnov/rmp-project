import Constants from 'expo-constants';
import { Candle, Order, Portfolio, Quote, User } from './types';

const baseUrl: string =
  process.env.EXPO_PUBLIC_API_BASE_URL ??
  (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl ??
  'http://185.182.108.214:8080';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const resp = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
  });
  const text = await resp.text();
  if (!resp.ok) throw new ApiError(resp.status, text || resp.statusText);
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export const api = {
  baseUrl,
  wsUrl: baseUrl.replace(/^http/, 'ws'),

  login: (username: string) =>
    request<User>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username }) }),

  register: (username: string, email: string, initialBalance: number) =>
    request<User>('/api/users', {
      method: 'POST',
      body: JSON.stringify({ username, email, initialBalance }),
    }),

  user: (id: string) => request<User>(`/api/users/${id}`),

  deposit: (userId: string, amount: number) =>
    request<unknown>(`/api/accounts/${userId}/deposit`, {
      method: 'POST',
      body: JSON.stringify({ amount }),
    }),

  quotes: () => request<Quote[]>(`/api/quotes`),
  quote: (ticker: string) => request<Quote>(`/api/quotes/${ticker}`),
  candles: (ticker: string, from: number, to: number, interval: string) =>
    request<Candle[]>(`/api/quotes/${ticker}/candles?from=${from}&to=${to}&interval=${interval}`),

  portfolio: (userId: string) => request<Portfolio>(`/api/portfolio/${userId}`),

  marketTrade: (userId: string, ticker: string, action: 'BUY' | 'SELL', lots: number, pricePerLot: number) =>
    request<unknown>(`/api/trades`, {
      method: 'POST',
      body: JSON.stringify({ userId, ticker, action, lots, pricePerLot }),
    }),

  placeOrder: (
    userId: string,
    ticker: string,
    side: 'BUY' | 'SELL',
    lots: number,
    limitPrice: number
  ) =>
    request<Order>(`/api/orders`, {
      method: 'POST',
      body: JSON.stringify({ userId, ticker, side, lots, limitPrice }),
    }),

  listOrders: (userId: string, status?: string) =>
    request<Order[]>(
      `/api/orders/${userId}${status ? `?status=${status}` : ''}`
    ),

  cancelOrder: (orderId: string) =>
    request<Order>(`/api/orders/${orderId}`, { method: 'DELETE' }),
};
