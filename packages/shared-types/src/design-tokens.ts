/**
 * Trippin' AI — Design System Tokens
 * Strict 8px grid, accessible color contrasts, and typography scale.
 */

export const DesignTokens = {
  spacing: {
    xxs: 4,
    xs: 8,
    sm: 12,
    md: 16,
    lg: 24,
    xl: 32,
    xxl: 48,
    xxxl: 64
  },
  radius: {
    none: 0,
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    full: 9999
  },
  colors: {
    light: {
      primary: '#0D6EFD', // Ocean Blue
      onPrimary: '#FFFFFF',
      primaryContainer: '#E7F0FE',
      onPrimaryContainer: '#042866',
      secondary: '#00A86B', // Emerald Teal
      onSecondary: '#FFFFFF',
      secondaryContainer: '#D1F4E4',
      onSecondaryContainer: '#003822',
      tertiary: '#FF7A00', // Sunset Coral
      onTertiary: '#FFFFFF',
      background: '#F8F9FA',
      onBackground: '#1A1C1E',
      surface: '#FFFFFF',
      onSurface: '#1A1C1E',
      surfaceVariant: '#EEF0F2',
      onSurfaceVariant: '#43474E',
      outline: '#73777F',
      error: '#BA1A1A',
      onError: '#FFFFFF',
      // Category accent colors
      categoryAttraction: '#6750A4',
      categoryFood: '#E2551A',
      categoryNature: '#2E7D32',
      categoryNightlife: '#9C27B0',
      categoryTransit: '#0288D1'
    },
    dark: {
      primary: '#A8C7FA',
      onPrimary: '#00315B',
      primaryContainer: '#004785',
      onPrimaryContainer: '#D3E3FD',
      secondary: '#76DAB1',
      onSecondary: '#003823',
      secondaryContainer: '#005134',
      onSecondaryContainer: '#93F8CC',
      tertiary: '#FFB786',
      onTertiary: '#502400',
      background: '#111315',
      onBackground: '#E2E2E6',
      surface: '#1A1C1E',
      onSurface: '#E2E2E6',
      surfaceVariant: '#43474E',
      onSurfaceVariant: '#C3C7CF',
      outline: '#8D9199',
      error: '#FFB4AB',
      onError: '#690005',
      categoryAttraction: '#CFBCFF',
      categoryFood: '#FF8B57',
      categoryNature: '#81C784',
      categoryNightlife: '#BA68C8',
      categoryTransit: '#4FC3F7'
    }
  },
  typography: {
    displayLarge: { fontSize: 57, lineHeight: 64, fontWeight: '400' },
    displayMedium: { fontSize: 45, lineHeight: 52, fontWeight: '400' },
    headlineLarge: { fontSize: 32, lineHeight: 40, fontWeight: '600' },
    headlineMedium: { fontSize: 28, lineHeight: 36, fontWeight: '600' },
    titleLarge: { fontSize: 22, lineHeight: 28, fontWeight: '500' },
    titleMedium: { fontSize: 16, lineHeight: 24, fontWeight: '500' },
    bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: '400' },
    bodyMedium: { fontSize: 14, lineHeight: 20, fontWeight: '400' },
    labelLarge: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
    labelSmall: { fontSize: 11, lineHeight: 16, fontWeight: '500' }
  }
} as const;
