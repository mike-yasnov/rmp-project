import React, { useEffect, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { Search } from 'lucide-react-native';
import { useTheme } from '../ThemeProvider';
import { TextField } from '../components/TextField';
import { PercentBadge } from '../components/PercentBadge';
import { Quote } from '../types';
import { api } from '../api';
import { useQuoteSocket } from '../ws';
import { formatMoney } from '../format';

export function Market({ onTickerPress }: { onTickerPress: (ticker: string) => void }) {
  const { palette } = useTheme();
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    const reload = async () => {
      try {
        const data = await api.quotes();
        if (mounted) {
          setQuotes(data.sort((a, b) => a.ticker.localeCompare(b.ticker)));
          setError(null);
        }
      } catch (e) {
        if (mounted) setError(String(e));
      }
    };
    reload();
    const t = setInterval(reload, 5000);
    return () => {
      mounted = false;
      clearInterval(t);
    };
  }, []);

  useQuoteSocket((q) => {
    setQuotes((prev) => prev.map((x) => (x.ticker === q.ticker ? { ...x, price: q.price } : x)));
  });

  const filtered = query
    ? quotes.filter((q) => q.ticker.toUpperCase().includes(query.toUpperCase()))
    : quotes;

  return (
    <View style={{ flex: 1, backgroundColor: palette.canvas }}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: palette.textPrimary }]}>Биржа</Text>
        <View style={{ height: 12 }} />
        <TextField
          value={query}
          onChangeText={setQuery}
          placeholder="Поиск по тикеру"
          autoCapitalize="characters"
          leftIcon={<Search size={18} color={palette.textMuted} />}
        />
      </View>
      {error && quotes.length === 0 ? (
        <Text style={{ color: palette.accentDown, paddingHorizontal: 16 }}>
          Не удалось загрузить котировки: {error}
        </Text>
      ) : null}
      <FlatList
        data={filtered}
        keyExtractor={(q) => q.ticker}
        renderItem={({ item }) => <Row q={item} onPress={() => onTickerPress(item.ticker)} />}
        ItemSeparatorComponent={() => <View style={{ height: 1, backgroundColor: palette.canvas }} />}
      />
    </View>
  );
}

function Row({ q, onPress }: { q: Quote; onPress: () => void }) {
  const { palette } = useTheme();
  return (
    <Pressable onPress={onPress} style={[styles.row, { backgroundColor: palette.surface }]}>
      <View style={{ flex: 1 }}>
        <Text style={[styles.ticker, { color: palette.textPrimary }]}>{q.ticker}</Text>
        <Text style={[styles.sub, { color: palette.textMuted }]}>Биржевой тикер</Text>
      </View>
      <View style={{ alignItems: 'flex-end' }}>
        <Text style={[styles.price, { color: palette.textPrimary }]}>{formatMoney(q.price)}</Text>
        <View style={{ height: 4 }} />
        <PercentBadge percent={q.changePercent24h ?? 0} abs={q.change24h ?? 0} />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  header: { padding: 16 },
  title: { fontSize: 26, fontWeight: '800' },
  row: { padding: 16, flexDirection: 'row', alignItems: 'center' },
  ticker: { fontWeight: '700', fontSize: 16 },
  sub: { fontSize: 11, marginTop: 2 },
  price: { fontWeight: '600', fontSize: 14 },
});
