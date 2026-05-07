import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Segmented } from '../components/Segmented';
import { useSession } from '../session';

export function Profile() {
  const { palette, mode, setMode } = useTheme();
  const { session, logout } = useSession();
  const initials = session?.username?.slice(0, 2).toUpperCase() ?? '';

  return (
    <View style={{ flex: 1, backgroundColor: palette.canvas, padding: 16 }}>
      <Text style={[styles.title, { color: palette.textPrimary }]}>Профиль</Text>
      <View style={{ height: 16 }} />

      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View style={[styles.avatar, { backgroundColor: palette.accentBrand }]}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <View style={{ flex: 1, marginLeft: 16 }}>
            <Text style={{ fontWeight: '700', fontSize: 16, color: palette.textPrimary }}>
              {session?.username ?? '—'}
            </Text>
            <Text style={{ color: palette.textSecondary, fontSize: 13, marginTop: 2 }}>
              {session?.email ?? '—'}
            </Text>
            <Text style={{ color: palette.textMuted, fontSize: 11, marginTop: 2 }}>
              ID: {session?.userId.slice(0, 8)}…
            </Text>
          </View>
        </View>
      </Card>
      <View style={{ height: 16 }} />

      <Card>
        <Text style={{ fontWeight: '600', fontSize: 14, color: palette.textPrimary }}>Тема</Text>
        <View style={{ height: 8 }} />
        <Segmented
          options={['Системная', 'Тёмная', 'Светлая']}
          selectedIndex={mode === 'dark' ? 1 : mode === 'light' ? 2 : 0}
          onSelect={(i) => setMode(i === 1 ? 'dark' : i === 2 ? 'light' : 'system')}
        />
      </Card>

      <View style={{ flex: 1 }} />
      <Button title="Выйти" color={palette.accentDown} onPress={logout} />
      <Text style={[styles.footer, { color: palette.textMuted }]}>
        HighLoad Invest · v0.2 · РМП ИТМО 2026
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 26, fontWeight: '800' },
  avatar: { width: 56, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#fff', fontWeight: '700', fontSize: 18 },
  footer: { fontSize: 11, marginTop: 12, textAlign: 'center' },
});
