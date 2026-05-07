import React from 'react';
import { Pressable, Text, StyleSheet, ActivityIndicator, View } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { radii } from '../theme';

interface Props {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  color?: string;
  variant?: 'primary' | 'secondary';
  fullWidth?: boolean;
}

export function Button({
  title,
  onPress,
  disabled,
  loading,
  color,
  variant = 'primary',
  fullWidth = true,
}: Props) {
  const { palette } = useTheme();
  const isPrimary = variant === 'primary';
  const bg = isPrimary ? color ?? palette.accentBrand : 'transparent';
  const fg = isPrimary ? '#FFFFFF' : palette.textPrimary;
  const border = isPrimary ? 'transparent' : palette.borderDefault;

  return (
    <Pressable
      style={({ pressed }) => [
        styles.btn,
        fullWidth && styles.fullWidth,
        {
          backgroundColor: disabled ? palette.borderDefault : bg,
          borderColor: border,
          opacity: pressed ? 0.85 : 1,
        },
      ]}
      onPress={() => !disabled && !loading && onPress()}
      disabled={disabled || loading}
    >
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <Text style={[styles.label, { color: disabled ? palette.textMuted : fg }]}>{title}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: {
    height: 52,
    borderRadius: radii.button,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  fullWidth: { alignSelf: 'stretch' },
  label: { fontSize: 15, fontWeight: '600' },
});
