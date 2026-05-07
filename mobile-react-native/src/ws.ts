import { useEffect, useRef } from 'react';
import { api } from './api';
import { Quote } from './types';

export function useQuoteSocket(onQuote: (q: Quote) => void) {
  const cb = useRef(onQuote);
  cb.current = onQuote;
  useEffect(() => {
    const ws = new WebSocket(`${api.wsUrl}/ws/quotes`);
    ws.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data as string);
        if (data && typeof data.ticker === 'string' && typeof data.price === 'number') {
          cb.current(data as Quote);
        }
      } catch {}
    };
    return () => {
      try {
        ws.close();
      } catch {}
    };
  }, []);
}

export function useOrderSocket(userId: string | null | undefined, onEvent: (msg: string) => void) {
  const cb = useRef(onEvent);
  cb.current = onEvent;
  useEffect(() => {
    if (!userId) return;
    const ws = new WebSocket(`${api.wsUrl}/ws/orders/${userId}`);
    ws.onmessage = (e) => cb.current(e.data as string);
    return () => {
      try {
        ws.close();
      } catch {}
    };
  }, [userId]);
}
