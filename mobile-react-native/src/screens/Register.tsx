import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../ThemeProvider';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { api } from '../api';
import { useSession } from '../session';

export function Register({ onBack }: { onBack: () => void }) {
  const { palette } = useTheme();
  const { login } = useSession();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [initial, setInitial] = useState('100000');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!username || !email || loading) return;
    setLoading(true);
    setError(null);
    try {
      const balance = parseInt(initial || '100000', 10);
      const u = await api.register(username.trim(), email.trim(), isNaN(balance) ? 100000 : balance);
      await login({ userId: u.id, username: u.username, email: u.email });
    } catch (e) {
      setError('Не удалось создать аккаунт');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: palette.canvas }}>
      <ScrollView contentContainerStyle={styles.body}>
        <Text style={[styles.back, { color: palette.textSecondary }]} onPress={onBack}>
          ← Назад
        </Text>
        <Text style={[styles.title, { color: palette.textPrimary }]}>Создайте аккаунт</Text>
        <Text style={[styles.subtitle, { color: palette.textSecondary }]}>
          Получите стартовый депозит и попробуйте торговлю без рисков.
        </Text>
        <View style={{ height: 24 }} />
        <TextField
          value={username}
          onChangeText={setUsername}
          placeholder="username"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <View style={{ height: 12 }} />
        <TextField
          value={email}
          onChangeText={setEmail}
          placeholder="email"
          autoCapitalize="none"
          keyboardType="email-address"
        />
        <View style={{ height: 12 }} />
        <TextField
          value={initial}
          onChangeText={(t) => setInitial(t.replace(/[^0-9]/g, ''))}
          placeholder="стартовый депозит, ₽"
          keyboardType="numeric"
        />
        <Text style={[styles.hint, { color: palette.textMuted }]}>
          Минимум 1 ₽. По умолчанию — 100 000 ₽.
        </Text>
        {error ? <Text style={{ color: palette.accentDown, marginTop: 8 }}>{error}</Text> : null}
        <View style={{ height: 24 }} />
        <Button
          title="Создать"
          onPress={submit}
          loading={loading}
          disabled={!username || !email}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  body: { padding: 24 },
  back: { fontSize: 14, marginBottom: 16 },
  title: { fontSize: 22, fontWeight: '700' },
  subtitle: { fontSize: 14, marginTop: 8, lineHeight: 20 },
  hint: { fontSize: 12, marginTop: 4, marginLeft: 8 },
});
