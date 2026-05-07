import React, { useEffect, useState } from 'react';
import { FlatList, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { PercentBadge } from '../components/PercentBadge';
import { Portfolio, Position } from '../types';
import { api } from '../api';
import { useSession } from '../session';
import { formatMoney, formatNumber } from '../format';

export function Account({ onTickerPress }: { onTickerPress: (ticker: string) => void }) {
  const { palette } = useTheme();
  const { session } = useSession();
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [depositOpen, setDepositOpen] = useState(false);
  const [depositAmount, setDepositAmount] = useState('10000');

  const reload = async () => {
    if (!session) return;
    try {
      const p = await api.portfolio(session.userId);
      setPortfolio(p);
      setError(null);
    } catch (e) {
      setError(String(e));
    }
  };

  useEffect(() => {
    reload();
    const t = setInterval(reload, 5000);
    return () => clearInterval(t);
  }, [session?.userId]);

  const submitDeposit = async () => {
    if (!session) return;
    const amt = parseInt(depositAmount || '0', 10);
    if (!amt) return;
    try {
      await api.deposit(session.userId, amt);
      setDepositOpen(false);
      reload();
    } catch (e) {
      setError(String(e));
    }
  };

  const positions = portfolio?.positions ?? [];

  return (
    <View style={{ flex: 1, backgroundColor: palette.canvas }}>
      <FlatList
        data={positions}
        keyExtractor={(p) => p.ticker}
        contentContainerStyle={{ padding: 16 }}
        ItemSeparatorComponent={() => <View style={{ height: 8 }} />}
        ListHeaderComponent={
          <View>
            <Text style={[styles.h1, { color: palette.textPrimary }]}>Счёт</Text>
            <View style={{ height: 12 }} />
            <Card>
              <Text style={[styles.label, { color: palette.textMuted }]}>Баланс</Text>
              <Text style={[styles.numLg, { color: palette.textPrimary }]}>
                {formatMoney(portfolio?.balance ?? 0)}
              </Text>
              <View style={{ height: 12 }} />
              <View style={styles.row}>
                <ValueCol
                  label="Доступно"
                  value={portfolio?.availableBalance ?? portfolio?.balance ?? 0}
                />
                <ValueCol label="Резерв" value={portfolio?.reservedBalance ?? 0} />
              </View>
              {portfolio && portfolio.totals && portfolio.totals.invested > 0 ? (
                <>
                  <View style={[styles.divider, { backgroundColor: palette.borderSubtle }]} />
                  <View style={[styles.row, { alignItems: 'center' }]}>
                    <View style={{ flex: 1 }}>
                      <Text style={[styles.label, { color: palette.textMuted }]}>
                        Стоимость портфеля
                      </Text>
                      <Text style={[styles.numMd, { color: palette.textPrimary }]}>
                        {formatMoney(portfolio.totals.marketValue)}
                      </Text>
                    </View>
                    <PercentBadge
                      percent={portfolio.totals.unrealizedPnlPercent}
                      abs={portfolio.totals.unrealizedPnl}
                    />
                  </View>
                </>
              ) : null}
              <View style={{ height: 16 }} />
              <Button title="Пополнить" onPress={() => setDepositOpen(true)} />
            </Card>
            <View style={{ height: 16 }} />
            <Text style={[styles.h2, { color: palette.textPrimary }]}>Позиции</Text>
            {positions.length === 0 ? (
              <View style={{ marginTop: 8 }}>
                <Card>
                  <Text style={{ color: palette.textSecondary }}>
                    У вас пока нет открытых позиций.
                  </Text>
                  <Text style={{ color: palette.textMuted, fontSize: 12, marginTop: 4 }}>
                    Откройте «Биржу» внизу и купите первую бумагу.
                  </Text>
                </Card>
              </View>
            ) : null}
            <View style={{ height: 8 }} />
          </View>
        }
        ListFooterComponent={
          error ? (
            <Text style={{ color: palette.accentDown, marginTop: 8 }}>Ошибка: {error}</Text>
          ) : null
        }
        renderItem={({ item }) => <PositionRow p={item} onPress={() => onTickerPress(item.ticker)} />}
      />

      <Modal visible={depositOpen} animationType="slide" transparent onRequestClose={() => setDepositOpen(false)}>
        <Pressable style={styles.backdrop} onPress={() => setDepositOpen(false)}>
          <Pressable style={[styles.sheet, { backgroundColor: palette.elevated }]} onPress={() => {}}>
            <Text style={[styles.h2, { color: palette.textPrimary }]}>Пополнить счёт</Text>
            <View style={{ height: 12 }} />
            <TextField
              value={depositAmount}
              onChangeText={(t) => setDepositAmount(t.replace(/[^0-9]/g, ''))}
              placeholder="сумма, ₽"
              keyboardType="numeric"
            />
            <View style={{ height: 16 }} />
            <Button title="Пополнить" onPress={submitDeposit} />
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

function ValueCol({ label, value }: { label: string; value: number }) {
  const { palette } = useTheme();
  return (
    <View style={{ flex: 1 }}>
      <Text style={[styles.label, { color: palette.textMuted }]}>{label}</Text>
      <Text style={[styles.numSm, { color: palette.textPrimary }]}>{formatMoney(value)}</Text>
    </View>
  );
}

function PositionRow({ p, onPress }: { p: Position; onPress: () => void }) {
  const { palette } = useTheme();
  return (
    <Pressable onPress={onPress}>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View style={{ flex: 1 }}>
            <Text style={{ fontWeight: '700', color: palette.textPrimary, fontSize: 16 }}>{p.ticker}</Text>
            <Text style={{ color: palette.textSecondary, fontSize: 13, marginTop: 2 }}>
              {p.lots} лот · средняя {formatNumber(p.avgPrice)} ₽
            </Text>
          </View>
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={{ color: palette.textPrimary, fontSize: 13, fontWeight: '600' }}>
              {formatMoney(p.marketValue ?? p.lots * p.avgPrice)}
            </Text>
            <View style={{ height: 4 }} />
            <PercentBadge percent={p.unrealizedPnlPercent ?? 0} abs={p.unrealizedPnl ?? 0} />
          </View>
        </View>
      </Card>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: 26, fontWeight: '800' },
  h2: { fontSize: 16, fontWeight: '600' },
  label: { fontSize: 12, fontWeight: '500' },
  numLg: { fontSize: 28, fontWeight: '700', marginTop: 4 },
  numMd: { fontSize: 18, fontWeight: '600', marginTop: 2 },
  numSm: { fontSize: 14, fontWeight: '600', marginTop: 2 },
  row: { flexDirection: 'row', gap: 12 },
  divider: { height: 1, marginVertical: 12 },
  backdrop: { flex: 1, backgroundColor: '#00000080', justifyContent: 'flex-end' },
  sheet: { padding: 24, borderTopLeftRadius: 16, borderTopRightRadius: 16 },
});
