import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  FlatList,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { findUserApi, updateProfileApi, getMyProfileApi } from '@core/userService';
import {
  listFriendsApi,
  sendFriendRequestApi,
  acceptFriendRequestApi,
  type Friend,
} from '@core/friendService';
import { createDirectConversationApi } from '@core/conversationService';
import { updateStoredUser } from '@core/authSession';
import type { AuthUser } from '@core/types/auth';

type Mode = 'friends' | 'requests' | 'profile';

export default function ContactsPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>('friends');
  const [query, setQuery] = useState('');
  const [friends, setFriends] = useState<Friend[]>([]);
  const [found, setFound] = useState<AuthUser | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [avatar, setAvatar] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const loadFriends = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      setFriends(await listFriendsApi());
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot load friends');
    } finally {
      setBusy(false);
    }
  }, []);

  const loadProfile = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      const profile = await getMyProfileApi();
      setDisplayName(profile.displayName || '');
      setAvatar(profile.avatar || '');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot load profile');
    } finally {
      setBusy(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      if (mode === 'friends') {
        void loadFriends();
      } else if (mode === 'profile') {
        void loadProfile();
      }
    }, [mode, loadFriends, loadProfile]),
  );

  const handleSearch = async () => {
    const trimmed = query.trim();
    if (!trimmed) return;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      setFound(await findUserApi(trimmed));
    } catch (err: unknown) {
      setFound(null);
      setError(err instanceof Error ? err.message : 'User not found');
    } finally {
      setBusy(false);
    }
  };

  const openDirect = async (user: Friend | AuthUser) => {
    setBusy(true);
    setError('');
    try {
      const conversation = await createDirectConversationApi({ userId: user.userId });
      router.push({
        pathname: '/(tabs)/chat',
        params: {
          conversationId: conversation.conversationId,
          conversationType: 'DIRECT',
          title: user.displayName || user.username,
        },
      });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot open chat');
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Contacts</Text>
      <View style={styles.tabs}>
        {([
          ['friends', 'Friends'],
          ['requests', 'Requests'],
          ['profile', 'Profile'],
        ] as const).map(([key, label]) => (
          <Pressable
            key={key}
            style={[styles.tab, mode === key && styles.tabActive]}
            onPress={() => {
              setMode(key);
              setError('');
              setMessage('');
              setFound(null);
            }}
          >
            <Text style={[styles.tabText, mode === key && styles.tabTextActive]}>{label}</Text>
          </Pressable>
        ))}
      </View>

      {error ? <Text style={styles.error}>{error}</Text> : null}
      {message ? <Text style={styles.message}>{message}</Text> : null}
      {busy ? <ActivityIndicator color="#0068ff" /> : null}

      {mode === 'friends' ? (
        <FlatList
          data={friends}
          keyExtractor={(item) => item.userId}
          ListEmptyComponent={<Text style={styles.hint}>No friends yet.</Text>}
          renderItem={({ item }) => (
            <Pressable style={styles.row} onPress={() => void openDirect(item)}>
              <Text style={styles.rowTitle}>{item.displayName || item.username}</Text>
              <Text style={styles.rowSub}>@{item.username}</Text>
            </Pressable>
          )}
        />
      ) : null}

      {mode === 'requests' ? (
        <View>
          <View style={styles.searchRow}>
            <TextInput
              style={styles.input}
              placeholder="Username or email"
              value={query}
              onChangeText={setQuery}
              autoCapitalize="none"
            />
            <Pressable style={styles.button} onPress={() => void handleSearch()} disabled={busy}>
              <Text style={styles.buttonText}>Search</Text>
            </Pressable>
          </View>
          {found ? (
            <View style={styles.card}>
              <Text style={styles.rowTitle}>{found.displayName || found.username}</Text>
              <Text style={styles.rowSub}>@{found.username}</Text>
              <View style={styles.actions}>
                <Pressable
                  style={styles.button}
                  onPress={() => {
                    void (async () => {
                      setBusy(true);
                      setError('');
                      try {
                        await sendFriendRequestApi(found.userId);
                        setMessage('Friend request sent');
                      } catch (err: unknown) {
                        setError(err instanceof Error ? err.message : 'Send failed');
                      } finally {
                        setBusy(false);
                      }
                    })();
                  }}
                >
                  <Text style={styles.buttonText}>Send</Text>
                </Pressable>
                <Pressable
                  style={[styles.button, styles.buttonSecondary]}
                  onPress={() => {
                    void (async () => {
                      setBusy(true);
                      setError('');
                      try {
                        await acceptFriendRequestApi(found.userId);
                        setMessage('Friend request accepted');
                        await loadFriends();
                      } catch (err: unknown) {
                        setError(err instanceof Error ? err.message : 'Accept failed');
                      } finally {
                        setBusy(false);
                      }
                    })();
                  }}
                >
                  <Text style={styles.buttonText}>Accept</Text>
                </Pressable>
              </View>
            </View>
          ) : (
            <Text style={styles.hint}>Search a user to send or accept a request.</Text>
          )}
        </View>
      ) : null}

      {mode === 'profile' ? (
        <View style={styles.card}>
          <Text style={styles.label}>Display name</Text>
          <TextInput style={styles.input} value={displayName} onChangeText={setDisplayName} />
          <Text style={styles.label}>Avatar URL</Text>
          <TextInput style={styles.input} value={avatar} onChangeText={setAvatar} autoCapitalize="none" />
          <Pressable
            style={styles.button}
            onPress={() => {
              void (async () => {
                setBusy(true);
                setError('');
                setMessage('');
                try {
                  const updated = await updateProfileApi({
                    displayName: displayName.trim() || null,
                    avatar: avatar.trim() || null,
                  });
                  updateStoredUser(updated);
                  setMessage('Profile saved');
                } catch (err: unknown) {
                  setError(err instanceof Error ? err.message : 'Save failed');
                } finally {
                  setBusy(false);
                }
              })();
            }}
          >
            <Text style={styles.buttonText}>Save profile</Text>
          </Pressable>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 16 },
  title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
  tabs: { flexDirection: 'row', gap: 8, marginBottom: 12 },
  tab: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
  },
  tabActive: { backgroundColor: '#0068ff' },
  tabText: { color: '#334155', fontWeight: '600' },
  tabTextActive: { color: '#fff' },
  searchRow: { flexDirection: 'row', gap: 8, marginBottom: 12 },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginBottom: 8,
  },
  button: {
    backgroundColor: '#0068ff',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
    alignItems: 'center',
  },
  buttonSecondary: { backgroundColor: '#0369a1' },
  buttonText: { color: '#fff', fontWeight: '600' },
  row: {
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
  },
  rowTitle: { fontSize: 16, fontWeight: '600', color: '#0f172a' },
  rowSub: { color: '#64748b', marginTop: 2 },
  card: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    padding: 14,
    backgroundColor: '#f8fafc',
  },
  actions: { flexDirection: 'row', gap: 8, marginTop: 12 },
  label: { color: '#64748b', marginBottom: 4, marginTop: 4 },
  error: { color: '#dc2626', marginBottom: 8 },
  message: { color: '#15803d', marginBottom: 8 },
  hint: { color: '#64748b', marginTop: 8 },
});
