import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  API_BASE_URL,
  Candle,
  Portfolio,
  Quote,
  User,
  createTrade,
  createUser,
  deposit,
  getCandles,
  getPortfolio,
  getQuotes,
  quotesWebSocketUrl,
} from './api';

export default function App() {
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [selectedTicker, setSelectedTicker] = useState('SBER');
  const [user, setUser] = useState<User | null>(null);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [lots, setLots] = useState('1');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(`API: ${API_BASE_URL}`);
  const [username, setUsername] = useState(`rn_${Date.now() % 100000}`);

  const selectedQuote = useMemo(
    () => quotes.find((quote) => quote.ticker === selectedTicker) ?? quotes[0],
    [quotes, selectedTicker],
  );

  const runAction = useCallback(async (successMessage: string, action: () => Promise<void>) => {
    setLoading(true);
    try {
      await action();
      setMessage(successMessage);
    } catch (error) {
      const text = error instanceof Error ? error.message : String(error);
      setMessage(text);
      Alert.alert('Ошибка', text);
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshQuotes = useCallback(async () => {
    const nextQuotes = (await getQuotes()).sort((a, b) => a.ticker.localeCompare(b.ticker));
    setQuotes(nextQuotes);
    if (nextQuotes.length > 0 && !nextQuotes.some((quote) => quote.ticker === selectedTicker)) {
      setSelectedTicker(nextQuotes[0]?.ticker ?? selectedTicker);
    }
  }, [selectedTicker]);

  const refreshCandles = useCallback(async (ticker: string) => {
    const nextCandles = await getCandles(ticker);
    setCandles(nextCandles.slice(-30));
  }, []);

  const refreshPortfolio = useCallback(async (userId: string) => {
    setPortfolio(await getPortfolio(userId));
  }, []);

  useEffect(() => {
    refreshQuotes().catch((error) => setMessage(error instanceof Error ? error.message : String(error)));
    const timer = setInterval(() => {
      refreshQuotes().catch(() => undefined);
    }, 5000);
    return () => clearInterval(timer);
  }, [refreshQuotes]);

  useEffect(() => {
    refreshCandles(selectedTicker).catch(() => undefined);
  }, [refreshCandles, selectedTicker]);

  useEffect(() => {
    const ws = new WebSocket(quotesWebSocketUrl());
    ws.onmessage = (event) => {
      try {
        const quote = JSON.parse(event.data) as Quote;
        if (!quote.ticker) return;
        setQuotes((current) =>
          [...current.filter((item) => item.ticker !== quote.ticker), quote].sort((a, b) =>
            a.ticker.localeCompare(b.ticker),
          ),
        );
      } catch {
        // Ignore service messages like {"type":"connected"}.
      }
    };
    ws.onerror = () => setMessage('WebSocket недоступен, используется polling');
    return () => ws.close();
  }, []);

  const register = () =>
    runAction('Пользователь создан', async () => {
      const created = await createUser(username, `${username}@example.test`);
      setUser(created);
      await refreshPortfolio(created.id);
    });

  const addMoney = () =>
    runAction('Счёт пополнен', async () => {
      if (!user) throw new Error('Сначала зарегистрируйтесь');
      await deposit(user.id, 50_000);
      await refreshPortfolio(user.id);
    });

  const submitTrade = (action: 'BUY' | 'SELL') =>
    runAction('Заявка исполнена', async () => {
      if (!user) throw new Error('Сначала зарегистрируйтесь');
      if (!selectedQuote) throw new Error('Нет выбранной котировки');
      const parsedLots = Math.max(1, Number.parseInt(lots, 10) || 1);
      await createTrade(user.id, selectedQuote.ticker, action, parsedLots, selectedQuote.price);
      await refreshPortfolio(user.id);
    });

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>HighLoad Invest</Text>
          <Text style={styles.subtitle}>React Native Android</Text>
        </View>
        {loading ? <ActivityIndicator /> : <Text style={styles.badge}>live</Text>}
      </View>

      <Card>
        <Text style={styles.sectionTitle}>Аккаунт</Text>
        {user ? (
          <>
            <Text style={styles.muted}>{user.username}</Text>
            <Pressable style={styles.secondaryButton} onPress={addMoney}>
              <Text style={styles.secondaryButtonText}>Пополнить на 50 000 RUB</Text>
            </Pressable>
          </>
        ) : (
          <>
            <TextInput value={username} onChangeText={setUsername} style={styles.input} placeholder="Логин" />
            <Pressable style={styles.primaryButton} onPress={register}>
              <Text style={styles.primaryButtonText}>Зарегистрироваться</Text>
            </Pressable>
          </>
        )}
      </Card>

      <Card>
        <View style={styles.rowBetween}>
          <Text style={styles.sectionTitle}>Котировки</Text>
          <Text style={styles.muted}>{quotes.length} тикеров</Text>
        </View>
        {quotes.map((quote) => (
          <Pressable
            key={quote.ticker}
            style={[styles.quoteRow, quote.ticker === selectedTicker && styles.quoteRowSelected]}
            onPress={() => setSelectedTicker(quote.ticker)}
          >
            <Text style={styles.ticker}>{quote.ticker}</Text>
            <Text style={styles.price}>{money(quote.price)} RUB</Text>
          </Pressable>
        ))}
        <Text style={styles.chartTitle}>Свечной график</Text>
        <CandleChart candles={candles} />
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>Заявка</Text>
        <Text style={styles.muted}>
          {selectedQuote ? `${selectedQuote.ticker}: ${money(selectedQuote.price)} RUB` : 'Нет цены'}
        </Text>
        <View style={styles.tradeRow}>
          <TextInput
            value={lots}
            onChangeText={(value) => setLots(value.replace(/\D/g, '') || '1')}
            style={[styles.input, styles.lotsInput]}
            keyboardType="number-pad"
            placeholder="Лоты"
          />
          <Text style={styles.total}>
            Итого {money((selectedQuote?.price ?? 0) * (Number.parseInt(lots, 10) || 1))}
          </Text>
        </View>
        <View style={styles.tradeRow}>
          <Pressable style={styles.primaryButton} onPress={() => submitTrade('BUY')}>
            <Text style={styles.primaryButtonText}>Купить</Text>
          </Pressable>
          <Pressable style={styles.secondaryButton} onPress={() => submitTrade('SELL')}>
            <Text style={styles.secondaryButtonText}>Продать</Text>
          </Pressable>
        </View>
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>Портфель</Text>
        {portfolio ? (
          <>
            <Text style={styles.balance}>
              {money(portfolio.balance)} {portfolio.currency}
            </Text>
            {portfolio.positions.map((position) => (
              <View key={position.ticker} style={styles.rowBetween}>
                <Text style={styles.ticker}>{position.ticker}</Text>
                <Text style={styles.muted}>
                  {position.lots} лот. · avg {money(position.avgPrice)}
                </Text>
              </View>
            ))}
          </>
        ) : (
          <Text style={styles.muted}>Зарегистрируйтесь, чтобы увидеть портфель</Text>
        )}
      </Card>

      <Text style={styles.status}>{message}</Text>
    </ScrollView>
  );
}

function CandleChart({ candles }: { candles: Candle[] }) {
  if (candles.length === 0) {
    return <View style={styles.chart} />;
  }

  const min = Math.min(...candles.map((candle) => candle.low));
  const max = Math.max(...candles.map((candle) => candle.high));
  const range = max - min || 1;

  return (
    <View style={styles.chart}>
      {candles.map((candle, index) => {
        const top = ((max - candle.high) / range) * 132;
        const wickHeight = Math.max(2, ((candle.high - candle.low) / range) * 132);
        const bodyTop = ((max - Math.max(candle.open, candle.close)) / range) * 132;
        const bodyHeight = Math.max(3, (Math.abs(candle.close - candle.open) / range) * 132);
        const positive = candle.close >= candle.open;
        return (
          <View key={`${candle.timestamp}-${index}`} style={styles.candleSlot}>
            <View style={[styles.wick, { top, height: wickHeight, backgroundColor: positive ? '#22C55E' : '#EF4444' }]} />
            <View
              style={[
                styles.candleBody,
                { top: bodyTop, height: bodyHeight, backgroundColor: positive ? '#22C55E' : '#EF4444' },
              ]}
            />
          </View>
        );
      })}
    </View>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return <View style={styles.card}>{children}</View>;
}

function money(value: number): string {
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#F6F7F9',
  },
  content: {
    padding: 16,
    gap: 12,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: 26,
    fontWeight: '800',
    color: '#111827',
  },
  subtitle: {
    color: '#69727D',
    marginTop: 2,
  },
  badge: {
    color: '#0F766E',
    fontWeight: '700',
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    padding: 14,
    gap: 10,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#111827',
  },
  muted: {
    color: '#69727D',
  },
  input: {
    borderWidth: 1,
    borderColor: '#D8DEE5',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#FFFFFF',
  },
  primaryButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: '#0F766E',
    borderRadius: 8,
    paddingVertical: 12,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontWeight: '800',
  },
  secondaryButton: {
    flex: 1,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#0F766E',
    borderRadius: 8,
    paddingVertical: 12,
  },
  secondaryButtonText: {
    color: '#0F766E',
    fontWeight: '800',
  },
  rowBetween: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  quoteRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderRadius: 8,
    padding: 10,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#ECEFF3',
  },
  quoteRowSelected: {
    backgroundColor: '#E0F2FE',
    borderColor: '#7DD3FC',
  },
  ticker: {
    fontWeight: '800',
    color: '#111827',
  },
  price: {
    color: '#111827',
    fontWeight: '600',
  },
  chartTitle: {
    fontWeight: '700',
    color: '#334155',
    marginTop: 6,
  },
  chart: {
    height: 148,
    flexDirection: 'row',
    alignItems: 'stretch',
    backgroundColor: '#101820',
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 8,
    overflow: 'hidden',
  },
  candleSlot: {
    flex: 1,
    position: 'relative',
    alignItems: 'center',
  },
  wick: {
    position: 'absolute',
    width: 2,
  },
  candleBody: {
    position: 'absolute',
    width: 8,
    borderRadius: 2,
  },
  tradeRow: {
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
  },
  lotsInput: {
    flex: 1,
  },
  total: {
    flex: 1,
    color: '#334155',
    fontWeight: '700',
  },
  balance: {
    fontSize: 22,
    fontWeight: '800',
    color: '#111827',
  },
  status: {
    color: '#49515A',
    marginBottom: 24,
  },
});
