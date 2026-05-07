import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ArrowUp, ArrowDown } from 'lucide-react-native';
import { useTheme } from '../ThemeProvider';
import { formatNumber } from '../format';

interface Props {
  percent: number;
  abs?: number;
}

export function PercentBadge({ percent, abs }: Props) {
  const { palette } = useTheme();
  const isUp = percent >= 0;
  const color = isUp ? palette.accentUp : palette.accentDown;
  const sign = isUp ? '+' : '−';
  const Icon = isUp ? ArrowUp : ArrowDown;
  const absText = abs != null ? `, ${sign}${formatNumber(Math.abs(abs))} ₽` : '';
  return (
    <View style={[styles.badge, { backgroundColor: color + '26' }]}>
      <Icon size={14} color={color} strokeWidth={2.5} />
      <Text style={{ color, fontWeight: '600', fontSize: 12, marginLeft: 2 }}>
        {sign}
        {Math.abs(percent).toFixed(2)}%{absText}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
  },
});
