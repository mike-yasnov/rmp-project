import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ThemeProvider';

interface Props {
  options: string[];
  selectedIndex: number;
  onSelect: (i: number) => void;
}

export function ChipRow({ options, selectedIndex, onSelect }: Props) {
  const { palette } = useTheme();
  return (
    <View style={styles.row}>
      {options.map((label, i) => {
        const active = i === selectedIndex;
        return (
          <Pressable
            key={label}
            style={[
              styles.chip,
              {
                backgroundColor: active ? palette.accentBrand + '26' : palette.elevated,
                borderColor: active ? palette.accentBrand + '66' : palette.borderSubtle,
              },
            ]}
            onPress={() => onSelect(i)}
          >
            <Text
              style={{
                color: active ? palette.accentBrand : palette.textSecondary,
                fontSize: 12,
                fontWeight: '600',
              }}
            >
              {label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
  },
});
