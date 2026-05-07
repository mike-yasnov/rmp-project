import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../ThemeProvider';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { api, ApiError } from '../api';
import { useSession } from '../session';

export function Login({ onBack }: { onBack: () => void }) {
  const { palette } = useTheme();
  const { login } = useSession();
  const [username, setUsername] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!username.trim() || loading) return;
    setLoading(true);
    setError(null);
    try {
      const u = await api.login(username.trim());
      await login({ userId: u.id, username: u.username, email: u.email });
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) setError('Пользователь не найден');
      else setError('Ошибка соединения');
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
        <Text style={[styles.title, { color: palette.textPrimary }]}>Войдите по имени пользователя</Text>
        <Text style={[styles.subtitle, { color: palette.textSecondary }]}>
          Учебный проект — паролей нет. Введите username, который указали при регистрации.
        </Text>
        <View style={{ height: 24 }} />
        <TextField
          value={username}
          onChangeText={(t) => {
            setUsername(t);
            setError(null);
          }}
          placeholder="username"
          autoCapitalize="none"
          autoCorrect={false}
          isError={error != null}
        />
        {error ? <Text style={{ color: palette.accentDown, marginTop: 8 }}>{error}</Text> : null}
        <View style={{ height: 24 }} />
        <Button title="Войти" onPress={submit} loading={loading} disabled={!username.trim()} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  body: { padding: 24 },
  back: { fontSize: 14, marginBottom: 16 },
  title: { fontSize: 22, fontWeight: '700' },
  subtitle: { fontSize: 14, marginTop: 8, lineHeight: 20 },
});
