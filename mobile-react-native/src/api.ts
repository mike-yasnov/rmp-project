import Constants from 'expo-constants';

export type Quote = {
  ticker: string;
  price: number;
  volume: number;
  timestamp: string;
};

export type Candle = {
  ticker: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  timestamp: string;
};

export type User = {
  id: string;
  username: string;
  email: string;
  balance: number;
};

export type Portfolio = {
  balance: number;
  currency: string;
  positions: Array<{
    ticker: string;
    lots: number;
    avgPrice: number;
  }>;
};

export type Trade = {
  id: string;
  userId: string;
  ticker: string;
  action: 'BUY' | 'SELL';
  lots: number;
  pricePerLot: number;
  totalAmount: number;
  createdAt: string;
};

const extra = Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined;
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? extra?.apiBaseUrl ?? 'http://10.0.2.2:8080';

export async function getQuotes(): Promise<Quote[]> {
  return request('/api/quotes');
}

export async function getCandles(ticker: string): Promise<Candle[]> {
  const to = Math.floor(Date.now() / 1000);
  const from = to - 3600;
  return request(`/api/quotes/${encodeURIComponent(ticker)}/candles?from=${from}&to=${to}`);
}

export async function createUser(username: string, email: string): Promise<User> {
  return request('/api/users', {
    method: 'POST',
    body: JSON.stringify({ username, email, initialBalance: 1_000_000 }),
  });
}

export async function deposit(userId: string, amount: number): Promise<void> {
  await request(`/api/accounts/${encodeURIComponent(userId)}/deposit`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  });
}

export async function createTrade(
  userId: string,
  ticker: string,
  action: 'BUY' | 'SELL',
  lots: number,
  pricePerLot: number,
): Promise<Trade> {
  return request('/api/trades', {
    method: 'POST',
    body: JSON.stringify({ userId, ticker, action, lots, pricePerLot }),
  });
}

export async function getPortfolio(userId: string): Promise<Portfolio> {
  return request(`/api/portfolio/${encodeURIComponent(userId)}`);
}

export function quotesWebSocketUrl(): string {
  if (API_BASE_URL.startsWith('https://')) return API_BASE_URL.replace('https://', 'wss://') + '/ws/quotes';
  return API_BASE_URL.replace('http://', 'ws://') + '/ws/quotes';
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text}`);
  }
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
