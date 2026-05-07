import React, { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Segmented } from '../components/Segmented';
import { ChipRow } from '../components/ChipRow';
import { PercentBadge } from '../components/PercentBadge';
import { PriceChart } from '../components/PriceChart';
import { Candle, ChartType, Order, Portfolio, Quote, TIMEFRAMES } from '../types';
import { api } from '../api';
import { useSession } from '../session';
import { formatMoney, formatNumber } from '../format';

export function TickerDetail({
  ticker,
  onBack,
  onTrade,
}: {
  ticker: string;
  onBack: () => void;
  onTrade: (ticker: string, side: 'BUY' | 'SELL') => void;
}) {
  const { palette } = useTheme();
  const { session } = useSession();
  const [quote, setQuote] = useState<Quote | null>(null);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [tfIndex, setTfIndex] = useState(0);
  const [chartType, setChartType] = useState<ChartType>('candle');

  const tf = TIMEFRAMES[tfIndex]!;

  useEffect(() => {
    const load = async () => {
      try {
        setQuote(await api.quote(ticker));
      } catch {}
      if (session) {
        try {
          setPortfolio(await api.portfolio(session.userId));
          const list = await api.listOrders(session.userId, 'PENDING');
          setOrders(list.filter((o) => o.ticker === ticker));
        } catch {}
      }
    };
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [ticker, session?.userId]);

  useEffect(() => {
    const load = async () => {
      try {
        const now = Math.floor(Date.now() / 1000);
        const from = now - tf.seconds;
        setCandles(await api.candles(ticker, from, now, tf.interval));
      } catch {
        setCandles([]);
      }
    };
    load();
  }, [ticker, tfIndex]);

  const position = portfolio?.positions.find((p) => p.ticker === ticker);

  return (
    <View style={{ flex: 1, backgroundColor: palette.canvas }}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 80 }}>
        <Text onPress={onBack} style={{ color: palette.textSecondary, marginBottom: 8 }}>
          ← Назад
        </Text>
        <View style={styles.header}>
          <View style={{ flex: 1 }}>
            <Text style={[styles.ticker, { color: palette.textPrimary }]}>{ticker}</Text>
            <Text style={[styles.price, { color: palette.textSecondary }]}>
              {quote ? formatMoney(quote.price) : '—'}
            </Text>
          </View>
          {quote ? (
            <PercentBadge percent={quote.changePercent24h ?? 0} abs={quote.change24h ?? 0} />
          ) : null}
        </View>
        <View style={{ height: 12 }} />

        <Card>
          <Segmented
            options={['Линия', 'Свечи']}
            selectedIndex={chartType === 'line' ? 0 : 1}
            onSelect={(i) => setChartType(i === 0 ? 'line' : 'candle')}
          />
          <View style={{ height: 12 }} />
          <PriceChart candles={candles} type={chartType} />
          <View style={{ height: 12 }} />
          <ChipRow
            options={TIMEFRAMES.map((t) => t.label)}
            selectedIndex={tfIndex}
            onSelect={setTfIndex}
          />
        </Card>

        {position ? (
          <>
            <View style={{ height: 12 }} />
            <Card>
              <Text style={{ fontWeight: '600', fontSize: 14, color: palette.textPrimary }}>
                Ваша позиция
              </Text>
              <View style={{ height: 8 }} />
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: palette.textSecondary, fontSize: 14 }}>{position.lots} лот</Text>
                  <Text style={{ color: palette.textMuted, fontSize: 12, marginTop: 2 }}>
                    Средняя {formatNumber(position.avgPrice)} ₽
                  </Text>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <Text style={{ color: palette.textPrimary, fontWeight: '600' }}>
                    {formatMoney(position.marketValue ?? 0)}
                  </Text>
                  <View style={{ height: 4 }} />
                  <PercentBadge
                    percent={position.unrealizedPnlPercent ?? 0}
                    abs={position.unrealizedPnl ?? 0}
                  />
                </View>
              </View>
            </Card>
          </>
        ) : null}

        {orders.length > 0 ? (
          <>
            <View style={{ height: 12 }} />
            <Card>
              <Text style={{ fontWeight: '600', fontSize: 14, color: palette.textPrimary }}>
                Открытые заявки
              </Text>
              <View style={{ height: 8 }} />
              {orders.map((o) => {
                const c = o.side === 'BUY' ? palette.accentUp : palette.accentDown;
                return (
                  <View key={o.id} style={styles.orderRow}>
                    <View style={[styles.sideTag, { backgroundColor: c + '26' }]}>
                      <Text style={{ color: c, fontSize: 11, fontWeight: '700' }}>{o.side}</Text>
                    </View>
                    <Text style={{ color: palette.textPrimary, flex: 1, marginLeft: 8 }}>
                      {o.lots} × {formatNumber(o.limitPrice)} ₽
                    </Text>
                    <Text style={{ color: palette.textMuted, fontSize: 11 }}>{o.status}</Text>
                  </View>
                );
              })}
            </Card>
          </>
        ) : null}
      </ScrollView>

      <View style={[styles.bottomBar, { backgroundColor: palette.canvas }]}>
        <Button title="Купить" color={palette.accentUp} onPress={() => onTrade(ticker, 'BUY')} />
        <View style={{ width: 12 }} />
        <Button title="Продать" color={palette.accentDown} onPress={() => onTrade(ticker, 'SELL')} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center' },
  ticker: { fontWeight: '800', fontSize: 24 },
  price: { fontSize: 14, marginTop: 2 },
  orderRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6 },
  sideTag: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6 },
  bottomBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    padding: 16,
  },
});
