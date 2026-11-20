import React from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type RegisterFormProps = {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  error: string;
  message: string;
  isLoading: boolean;
  onUsernameChange: (value: string) => void;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onConfirmPasswordChange: (value: string) => void;
  onSubmit: () => void;
};

export default function RegisterForm({
  username,
  email,
  password,
  confirmPassword,
  error,
  message,
  isLoading,
  onUsernameChange,
  onEmailChange,
  onPasswordChange,
  onConfirmPasswordChange,
  onSubmit,
}: RegisterFormProps) {
  const theme = useTheme();

  const inputStyle = [
    styles.input,
    {
      borderColor: theme.inputBorder,
      backgroundColor: theme.inputBg,
      color: theme.text,
    },
  ];

  return (
    <View style={styles.formContainer}>
      <TextInput
        style={inputStyle}
        placeholder="Username"
        placeholderTextColor={theme.textMuted}
        value={username}
        onChangeText={onUsernameChange}
        autoCapitalize="none"
      />
      <TextInput
        style={inputStyle}
        placeholder="Email"
        placeholderTextColor={theme.textMuted}
        value={email}
        onChangeText={onEmailChange}
        keyboardType="email-address"
        autoCapitalize="none"
      />
      <TextInput
        style={inputStyle}
        placeholder="Password"
        placeholderTextColor={theme.textMuted}
        value={password}
        onChangeText={onPasswordChange}
        secureTextEntry
      />
      <TextInput
        style={inputStyle}
        placeholder="Confirm Password"
        placeholderTextColor={theme.textMuted}
        value={confirmPassword}
        onChangeText={onConfirmPasswordChange}
        secureTextEntry
      />
      {error ? (
        <Text style={[styles.errorText, { color: theme.danger }]}>{error}</Text>
      ) : null}
      {message ? (
        <Text style={[styles.successText, { color: theme.success }]}>{message}</Text>
      ) : null}
      <Pressable
        style={[
          styles.button,
          { backgroundColor: theme.primary },
          isLoading && styles.buttonDisabled,
        ]}
        onPress={onSubmit}
        disabled={isLoading}
      >
        <Text style={styles.buttonText}>
          {isLoading ? 'Creating account…' : 'Create account'}
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  formContainer: { gap: Spacing.three },
  input: {
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: Radius.md,
    fontSize: 15,
  },
  errorText: { fontSize: 14 },
  successText: { fontSize: 14 },
  button: {
    paddingVertical: 14,
    borderRadius: Radius.md,
    alignItems: 'center',
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#ffffff', fontWeight: '700', fontSize: 16 },
});
