import React from 'react';
import { TextInput, TextInputProps, View, StyleSheet } from 'react-native';
import { useTheme } from '../ThemeProvider';
import { radii } from '../theme';

interface Props extends TextInputProps {
  isError?: boolean;
  leftIcon?: React.ReactNode;
}

export function TextField({ isError, leftIcon, style, placeholderTextColor, ...rest }: Props) {
  const { palette } = useTheme();
  return (
    <View
      style={[
        styles.wrap,
        {
          backgroundColor: palette.elevated,
          borderColor: isError ? palette.accentDown : palette.borderDefault,
        },
      ]}
    >
      {leftIcon ? <View style={styles.leftIcon}>{leftIcon}</View> : null}
      <TextInput
        style={[styles.input, { color: palette.textPrimary }, style]}
        placeholderTextColor={placeholderTextColor ?? palette.textMuted}
        {...rest}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    minHeight: 52,
    borderRadius: radii.sm,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  leftIcon: { marginRight: 12 },
  input: { flex: 1, fontSize: 16, paddingVertical: 14 },
});
