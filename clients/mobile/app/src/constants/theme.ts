/**
 * Shared haloZalo palette — Zalo brand blue #0068FF.
 */

import '@/global.css';

import { Platform } from 'react-native';

export const Colors = {
  light: {
    text: '#0f172a',
    textSecondary: '#475569',
    textMuted: '#64748b',
    background: '#f7faff',
    backgroundElement: '#eef4ff',
    backgroundSelected: '#eff6ff',
    border: '#d7e0f0',
    inputBg: '#f7faff',
    inputBorder: '#c5d0e0',
    primary: '#0068ff',
    primaryHover: '#0052cc',
    primarySoft: '#eff6ff',
    danger: '#b91c1c',
    success: '#15803d',
    chatOwn: '#0068ff',
    chatOwnText: '#ffffff',
    chatOther: '#eef2f7',
    chatOtherText: '#0f172a',
  },
  dark: {
    text: '#e2e8f0',
    textSecondary: '#94a3b8',
    textMuted: '#64748b',
    background: '#0b1220',
    backgroundElement: '#111827',
    backgroundSelected: '#0b2a66',
    border: '#1e293b',
    inputBg: '#111827',
    inputBorder: '#334155',
    primary: '#4c9aff',
    primaryHover: '#7ab4ff',
    primarySoft: '#0b2a66',
    danger: '#f87171',
    success: '#4ade80',
    chatOwn: '#0068ff',
    chatOwnText: '#ffffff',
    chatOther: '#1e293b',
    chatOtherText: '#e2e8f0',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Fonts = Platform.select({
  ios: {
    sans: 'System',
    serif: 'ui-serif',
    rounded: 'ui-rounded',
    mono: 'ui-monospace',
  },
  default: {
    sans: 'sans-serif',
    serif: 'serif',
    rounded: 'sans-serif',
    mono: 'monospace',
  },
  web: {
    sans: 'var(--font-display)',
    serif: 'var(--font-serif)',
    rounded: 'var(--font-rounded)',
    mono: 'var(--font-mono)',
  },
});

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

export const Radius = {
  sm: 6,
  md: 10,
  lg: 14,
  xl: 20,
  full: 999,
} as const;

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0;
export const MaxContentWidth = 800;
