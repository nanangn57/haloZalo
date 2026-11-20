import React from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';

export default function RegisterForm({ username, email, password, confirmPassword, error, message, isLoading, onUsernameChange, onEmailChange, onPasswordChange, onConfirmPasswordChange, onSubmit }) {
  return (
    <View style={styles.formContainer}>
      <TextInput style={styles.input} placeholder="Username" value={username} onChangeText={onUsernameChange} autoCapitalize="none" />
      <TextInput style={styles.input} placeholder="Email" value={email} onChangeText={onEmailChange} keyboardType="email-address" autoCapitalize="none" />
      <TextInput style={styles.input} placeholder="Password" value={password} onChangeText={onPasswordChange} secureTextEntry />
      <TextInput style={styles.input} placeholder="Confirm Password" value={confirmPassword} onChangeText={onConfirmPasswordChange} secureTextEntry />
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      {message ? <Text style={styles.successText}>{message}</Text> : null}
      <Pressable style={styles.button} onPress={onSubmit} disabled={isLoading}>
        <Text style={styles.buttonText}>{isLoading ? 'Registering...' : 'Register'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  formContainer: { gap: 12 },
  input: { borderWidth: 1, borderColor: '#cbd5e1', padding: 12, borderRadius: 8, backgroundColor: '#f8fafc' },
  errorText: { color: '#dc2626' },
  successText: { color: '#16a34a' },
  button: { backgroundColor: '#4f46e5', padding: 14, borderRadius: 8, alignItems: 'center' },
  buttonText: { color: '#ffffff', fontWeight: 'bold' }
});