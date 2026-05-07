import React, { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { Segmented } from '../components/Segmented';
import { api } from '../api';
import { useSession } from '../session';
import { formatMoney } from '../format';

export function LimitOrder({
  ticker,
  side,
  onBack,
  onDone,
}: {
  ticker: string;
  side: 'BUY' | 'SELL';
  onBack: () => void;
  onDone: () => void;
}) {
  const { palette } = useTheme();
  const { session } = useSession();
  const isBuy = side === 'BUY';
  const sideColor = isBuy ? palette.accentUp : palette.accentDown;

  const [orderType, setOrderType] = useState(0); // 0 = Limit, 1 = Market
  const [lots, setLots] = useState('1');
  const [price, setPrice] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api
      .quote(ticker)
      .then((q) => setPrice(q.price.toFixed(2)))
      .catch(() => {});
  }, [ticker]);

  const lotsInt = parseInt(lots || '0', 10);
  const priceVal = parseFloat(price.replace(',', '.')) || 0;
  const total = lotsInt * priceVal;

  const submit = async () => {
    if (!session || !lotsInt || !priceVal || loading) return;
    setLoading(true);
    setError(null);
    try {
      if (orderType === 1) {
        await api.marketTrade(session.userId, ticker, side, lotsInt, priceVal);
      } else {
        await api.placeOrder(session.userId, ticker, side, lotsInt, priceVal);
      }
      onDone();
    } catch (e) {
      setError(`Не удалось: ${String(e).slice(0, 120)}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: palette.canvas }}>
      <ScrollView contentContainerStyle={{ padding: 16 }}>
        <Text onPress={onBack} style={{ color: palette.textSecondary }}>
          ← Назад
        </Text>
        <View style={{ height: 8 }} />
        <Text style={[styles.title, { color: palette.textPrimary }]}>
          {isBuy ? 'Купить' : 'Продать'} {ticker}
        </Text>
        <View style={{ height: 16 }} />
        <Segmented
          options={['Лимит', 'Рынок']}
          selectedIndex={orderType}
          onSelect={setOrderType}
        />
        <View style={{ height: 12 }} />

        <Card>
          <Text style={[styles.label, { color: palette.textMuted }]}>Количество, лот</Text>
          <View style={{ height: 6 }} />
          <TextField
            value={lots}
            onChangeText={(t) => setLots(t.replace(/[^0-9]/g, ''))}
            keyboardType="numeric"
            placeholder="1"
          />
        </Card>
        <View style={{ height: 12 }} />

        <Card>
          <Text style={[styles.label, { color: palette.textMuted }]}>
            {orderType === 0 ? 'Лимитная цена, ₽' : 'Цена исполнения (текущая)'}
          </Text>
          <View style={{ height: 6 }} />
          <TextField
            value={price}
            onChangeText={(t) => setPrice(t.replace(/[^0-9.,]/g, ''))}
            placeholder="0,00"
            keyboardType="decimal-pad"
            editable={orderType === 0}
          />
        </Card>
        <View style={{ height: 12 }} />

        <Card>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <View style={{ flex: 1 }}>
              <Text style={[styles.label, { color: palette.textMuted }]}>Итого</Text>
              <View style={{ height: 4 }} />
              <Text style={[styles.total, { color: palette.textPrimary }]}>
                {formatMoney(total)}
              </Text>
            </View>
            <Text style={{ color: palette.textMuted, fontSize: 12 }}>
              {isBuy ? 'к списанию' : 'к зачислению'}
            </Text>
          </View>
        </Card>

        {error ? (
          <Text style={{ color: palette.accentDown, marginTop: 12 }}>{error}</Text>
        ) : null}
        <View style={{ height: 24 }} />
        <Button
          title={isBuy ? 'Подтвердить покупку' : 'Подтвердить продажу'}
          color={sideColor}
          onPress={submit}
          loading={loading}
          disabled={!lotsInt || !priceVal}
        />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 22, fontWeight: '700' },
  label: { fontSize: 12, fontWeight: '500' },
  total: { fontSize: 18, fontWeight: '700' },
});
