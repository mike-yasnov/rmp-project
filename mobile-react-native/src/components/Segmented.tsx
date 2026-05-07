import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { radii } from '../theme';

interface Props {
  options: string[];
  selectedIndex: number;
  onSelect: (i: number) => void;
}

export function Segmented({ options, selectedIndex, onSelect }: Props) {
  const { palette } = useTheme();
  return (
    <View
      style={[
        styles.wrap,
        { backgroundColor: palette.elevated, borderColor: palette.borderSubtle },
      ]}
    >
      {options.map((label, i) => {
        const active = i === selectedIndex;
        return (
          <Pressable
            key={label}
            style={[
              styles.tab,
              {
                backgroundColor: active ? palette.surface : 'transparent',
              },
            ]}
            onPress={() => onSelect(i)}
          >
            <Text style={{ color: active ? palette.textPrimary : palette.textSecondary, fontWeight: active ? '600' : '500' }}>
              {label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    borderRadius: radii.sm,
    borderWidth: 1,
    padding: 4,
  },
  tab: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: radii.xs,
    alignItems: 'center',
  },
});
