export interface Palette {
  canvas: string;
  surface: string;
  elevated: string;
  borderDefault: string;
  borderSubtle: string;
  textPrimary: string;
  textSecondary: string;
  textMuted: string;
  textInverse: string;
  accentUp: string;
  accentDown: string;
  accentBrand: string;
  accentWarning: string;
  isDark: boolean;
}

export const dark: Palette = {
  canvas: '#0A0E14',
  surface: '#11161D',
  elevated: '#1A2029',
  borderDefault: '#232A35',
  borderSubtle: '#1A2029',
  textPrimary: '#E8ECF1',
  textSecondary: '#9AA4B2',
  textMuted: '#5C6776',
  textInverse: '#0A0E14',
  accentUp: '#00C853',
  accentDown: '#FF3B30',
  accentBrand: '#4F8FFF',
  accentWarning: '#FFB020',
  isDark: true,
};

export const light: Palette = {
  canvas: '#F6F7F9',
  surface: '#FFFFFF',
  elevated: '#FFFFFF',
  borderDefault: '#E3E6EB',
  borderSubtle: '#EEF0F3',
  textPrimary: '#101820',
  textSecondary: '#4A5260',
  textMuted: '#8B95A2',
  textInverse: '#FFFFFF',
  accentUp: '#00A046',
  accentDown: '#E5392F',
  accentBrand: '#1F6FF0',
  accentWarning: '#E39008',
  isDark: false,
};

export type ThemeMode = 'system' | 'dark' | 'light';

export const radii = { xs: 6, sm: 8, md: 12, lg: 16, xl: 20, button: 10 };
export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, xxxl: 32 };
