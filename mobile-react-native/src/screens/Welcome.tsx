import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../ThemeProvider';
import { Button } from '../components/Button';

export function Welcome({
  onLogin,
  onRegister,
}: {
  onLogin: () => void;
  onRegister: () => void;
}) {
  const { palette } = useTheme();
  return (
    <SafeAreaView style={[styles.root, { backgroundColor: palette.canvas }]}>
      <View style={styles.spacer} />
      <View style={styles.center}>
        <View style={[styles.logo, { backgroundColor: palette.accentBrand }]}>
          <Text style={styles.logoText}>HI</Text>
        </View>
        <Text style={[styles.title, { color: palette.textPrimary }]}>HighLoad Invest</Text>
        <Text style={[styles.subtitle, { color: palette.textSecondary }]}>
          Симулятор биржевых торгов{'\n'}для курса РМП ИТМО
        </Text>
      </View>
      <View style={styles.actions}>
        <Button title="Создать аккаунт" onPress={onRegister} />
        <View style={{ height: 12 }} />
        <Button title="У меня уже есть аккаунт" onPress={onLogin} variant="secondary" />
        <Text style={[styles.tiny, { color: palette.textMuted }]}>
          Учебный проект · без реальных денег
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, paddingHorizontal: 24, paddingVertical: 32, alignItems: 'stretch' },
  spacer: { flex: 1 },
  center: { alignItems: 'center', flex: 1.4 },
  logo: { width: 96, height: 96, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  logoText: { color: 'white', fontWeight: '900', fontSize: 36 },
  title: { fontSize: 32, fontWeight: '800', marginTop: 24, textAlign: 'center' },
  subtitle: { fontSize: 16, marginTop: 8, textAlign: 'center', lineHeight: 22 },
  actions: { paddingBottom: 8 },
  tiny: { textAlign: 'center', fontSize: 12, marginTop: 12 },
});
