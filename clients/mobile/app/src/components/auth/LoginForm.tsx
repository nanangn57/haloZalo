import React from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';

export default function LoginForm({ email, password, error, onUsernameChange, onPasswordChange, onSubmit, isLoading }) {
  return (
    <View style={styles.formContainer}>
      <View style={styles.inputGroup}>
        <Text style={styles.label}>Email</Text>
        <TextInput style={styles.input} placeholder="Enter your email" value={email} onChangeText={onUsernameChange} keyboardType="email-address" autoCapitalize="none" />
      </View>
      <View style={styles.inputGroup}>
        <Text style={styles.label}>Password</Text>
        <TextInput style={styles.input} placeholder="Enter your password" value={password} onChangeText={onPasswordChange} secureTextEntry />
      </View>
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      <Pressable style={[styles.button, isLoading && styles.buttonDisabled]} onPress={onSubmit} disabled={isLoading}>
        <Text style={styles.buttonText}>{isLoading ? 'Loading...' : 'Login'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  formContainer: { gap: 16 },
  inputGroup: { gap: 8 },
  label: { fontSize: 14, fontWeight: '500', color: '#1e293b' },
  input: { borderWidth: 1, borderColor: '#cbd5e1', padding: 12, borderRadius: 8, backgroundColor: '#f8fafc' },
  errorText: { color: '#dc2626', fontSize: 14 },
  button: { backgroundColor: '#4f46e5', padding: 14, borderRadius: 8, alignItems: 'center', marginTop: 8 },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#ffffff', fontWeight: 'bold', fontSize: 16 }
});