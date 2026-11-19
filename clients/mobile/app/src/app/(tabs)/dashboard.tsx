import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function DashboardPage() {
  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Dashboard (WIP)</Text>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({ container: { flex: 1, alignItems: 'center', justifyContent: 'center' }, title: { fontSize: 20 }});