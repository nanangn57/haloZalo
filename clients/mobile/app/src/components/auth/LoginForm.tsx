import React from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type LoginFormProps = {
  email: string;
  password: string;
  error: string;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onSubmit: () => void;
  isLoading: boolean;
};

export default function LoginForm({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onSubmit,
  isLoading,
}: LoginFormProps) {
  const theme = useTheme();

  return (
    <View style={styles.formContainer}>
      <View style={styles.inputGroup}>
        <Text style={[styles.label, { color: theme.text }]}>Email</Text>
        <TextInput
          style={[
            styles.input,
            {
              borderColor: theme.inputBorder,
              backgroundColor: theme.inputBg,
              color: theme.text,
            },
          ]}
          placeholder="you@example.com"
          placeholderTextColor={theme.textMuted}
          value={email}
          onChangeText={onEmailChange}
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
        />
      </View>
      <View style={styles.inputGroup}>
        <Text style={[styles.label, { color: theme.text }]}>Password</Text>
        <TextInput
          style={[
            styles.input,
            {
              borderColor: theme.inputBorder,
              backgroundColor: theme.inputBg,
              color: theme.text,
            },
          ]}
          placeholder="At least 8 characters"
          placeholderTextColor={theme.textMuted}
          value={password}
          onChangeText={onPasswordChange}
          secureTextEntry
        />
      </View>
      {error ? (
        <Text style={[styles.errorText, { color: theme.danger }]}>{error}</Text>
      ) : null}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Sign in"
        style={[
          styles.button,
          { backgroundColor: theme.primary },
          isLoading && styles.buttonDisabled,
        ]}
        onPress={onSubmit}
        disabled={isLoading}
      >
        <Text style={styles.buttonText}>
          {isLoading ? 'Signing in…' : 'Sign in'}
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  formContainer: { gap: Spacing.three },
  inputGroup: { gap: Spacing.two },
  label: { fontSize: 14, fontWeight: '500' },
  input: {
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: Radius.md,
    fontSize: 15,
  },
  errorText: { fontSize: 14 },
  button: {
    paddingVertical: 14,
    borderRadius: Radius.md,
    alignItems: 'center',
    marginTop: Spacing.one,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#ffffff', fontWeight: '700', fontSize: 16 },
});
