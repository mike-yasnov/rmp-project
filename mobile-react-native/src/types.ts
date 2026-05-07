export interface User {
  id: string;
  username: string;
  email: string;
  balance: number;
}

export interface Quote {
  ticker: string;
  price: number;
  volume?: number;
  timestamp?: string;
  change24h?: number;
  changePercent24h?: number;
}

export interface Candle {
  ticker: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  timestamp: string;
}

export interface Position {
  ticker: string;
  lots: number;
  avgPrice: number;
  currentPrice?: number;
  marketValue?: number;
  unrealizedPnl?: number;
  unrealizedPnlPercent?: number;
}

export interface PortfolioTotals {
  invested: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPercent: number;
}

export interface Portfolio {
  balance: number;
  reservedBalance?: number;
  availableBalance?: number;
  currency: string;
  positions: Position[];
  totals?: PortfolioTotals;
}

export interface Order {
  id: string;
  userId: string;
  ticker: string;
  side: 'BUY' | 'SELL';
  lots: number;
  limitPrice: number;
  status: 'PENDING' | 'FILLED' | 'CANCELLED' | 'INSUFFICIENT_FUNDS' | 'INSUFFICIENT_LOTS';
  reservedAmount: number;
  createdAt: string;
  filledAt?: string | null;
  cancelledAt?: string | null;
  fillTradeId?: string | null;
  fillPrice?: number | null;
}

export type ChartType = 'line' | 'candle';

export interface Timeframe {
  key: 'week' | 'month' | 'half_year' | 'year' | 'all';
  label: string;
  interval: '1m' | '5m' | '15m' | '1h' | '1d';
  seconds: number;
}

export const TIMEFRAMES: Timeframe[] = [
  { key: 'week', label: '1Н', interval: '5m', seconds: 7 * 24 * 3600 },
  { key: 'month', label: '1М', interval: '15m', seconds: 30 * 24 * 3600 },
  { key: 'half_year', label: '6М', interval: '1h', seconds: 180 * 24 * 3600 },
  { key: 'year', label: '1Г', interval: '1d', seconds: 365 * 24 * 3600 },
  { key: 'all', label: 'ВСЁ', interval: '1d', seconds: 5 * 365 * 24 * 3600 },
];
