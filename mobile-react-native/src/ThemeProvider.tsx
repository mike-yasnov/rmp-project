import React, { createContext, useContext, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { dark, light, Palette, ThemeMode } from './theme';

interface ThemeCtx {
  palette: Palette;
  mode: ThemeMode;
  setMode: (m: ThemeMode) => void;
}

const Ctx = createContext<ThemeCtx>({ palette: dark, mode: 'system', setMode: () => {} });

const KEY = 'theme.mode';

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const sys = useColorScheme();
  const [mode, setModeState] = useState<ThemeMode>('system');

  useEffect(() => {
    SecureStore.getItemAsync(KEY).then((m) => {
      if (m === 'dark' || m === 'light' || m === 'system') setModeState(m);
    });
  }, []);

  const setMode = (m: ThemeMode) => {
    setModeState(m);
    SecureStore.setItemAsync(KEY, m).catch(() => {});
  };

  const isDark = mode === 'dark' || (mode === 'system' && sys === 'dark');
  const palette = isDark ? dark : light;
  return <Ctx.Provider value={{ palette, mode, setMode }}>{children}</Ctx.Provider>;
}

export const useTheme = () => useContext(Ctx);
