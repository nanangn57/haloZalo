import React, { useCallback, useRef, useState } from 'react';
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
import {
  findUserApi,
  updateProfileApi,
  getMyProfileApi,
  lookupUsersApi,
  type UserSummary,
} from '@core/userService';
import {
  listFriendsApi,
  listFriendRequestsApi,
  sendFriendRequestApi,
  acceptFriendRequestApi,
  rejectFriendRequestApi,
  cancelFriendRequestApi,
  unfriendApi,
  blockUserApi,
  type Friend,
  type FriendRequestList,
} from '@core/friendService';
import { confirmAction } from '@/lib/confirm';
import { createDirectConversationApi } from '@core/conversationService';
import { updateStoredUser } from '@core/authSession';
import { getPresenceApi, type UserPresence } from '@core/presenceService';
import { getInitials } from '@core/avatar';
import type { AuthUser } from '@core/types/auth';

type Mode = 'friends' | 'requests' | 'profile';

const PRESENCE_POLL_MS = 30_000;

export default function ContactsPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>('friends');
  const [query, setQuery] = useState('');
  const [friends, setFriends] = useState<Friend[]>([]);
  const [presence, setPresence] = useState<Record<string, UserPresence>>({});
  const friendIdsRef = useRef<string[]>([]);
  const [found, setFound] = useState<AuthUser | null>(null);
  const [requests, setRequests] = useState<FriendRequestList>({ incoming: [], outgoing: [] });
  const [people, setPeople] = useState<Record<string, UserSummary>>({});
  const [requestTab, setRequestTab] = useState<'incoming' | 'outgoing'>('incoming');
  const [displayName, setDisplayName] = useState('');
  const [avatar, setAvatar] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const refreshPresence = useCallback(async () => {
    const ids = friendIdsRef.current;
    if (ids.length === 0) {
      setPresence({});
      return;
    }
    try {
      setPresence(await getPresenceApi(ids));
    } catch {
      // Presence là phụ: lỗi thì giữ trạng thái cũ, không chặn danh sách bạn.
    }
  }, []);

  const loadFriends = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      const list = await listFriendsApi();
      setFriends(list);
      friendIdsRef.current = list.map((friend) => friend.userId);
      void refreshPresence();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot load friends');
    } finally {
      setBusy(false);
    }
  }, [refreshPresence]);

  const loadRequests = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      const data = await listFriendRequestsApi();
      setRequests(data);
      const ids = [
        ...data.incoming.map((request) => request.fromUserId),
        ...data.outgoing.map((request) => request.toUserId),
      ].slice(0, 100);
      if (ids.length > 0) {
        const users = await lookupUsersApi(ids);
        setPeople((previous) => {
          const next = { ...previous };
          users.forEach((user) => {
            next[user.userId] = user;
          });
          return next;
        });
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot load friend requests');
    } finally {
      setBusy(false);
    }
  }, []);

  const runRequestAction = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(done);
      await loadRequests();
      await loadFriends();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  };

  const handleUnfriend = (friend: Friend) => {
    confirmAction('Unfriend', `Remove ${friend.displayName || friend.username} from friends?`, () => {
      void runRequestAction(() => unfriendApi(friend.userId), 'Unfriended');
    });
  };

  const handleBlock = (friend: Friend) => {
    confirmAction('Block', `Block ${friend.displayName || friend.username}?`, () => {
      void runRequestAction(() => blockUserApi(friend.userId), 'User blocked');
    });
  };

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
      } else if (mode === 'requests') {
        void loadRequests();
      } else if (mode === 'profile') {
        void loadProfile();
      }
    }, [mode, loadFriends, loadRequests, loadProfile]),
  );

  // Làm mới presence mỗi 30 giây khi đang xem tab Friends và màn hình đang focus.
  useFocusEffect(
    useCallback(() => {
      if (mode !== 'friends') return undefined;
      const timer = setInterval(() => void refreshPresence(), PRESENCE_POLL_MS);
      return () => clearInterval(timer);
    }, [mode, refreshPresence]),
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
            <View style={styles.friendRow}>
              <Pressable style={{ flex: 1 }} onPress={() => void openDirect(item)}>
                <View style={styles.friendMain}>
                  <View>
                    <View style={styles.avatar}>
                      <Text style={styles.avatarText}>
                        {getInitials(item.displayName || item.username)}
                      </Text>
                    </View>
                    <View
                      style={[
                        styles.presenceDot,
                        presence[item.userId]?.online ? styles.presenceOnline : styles.presenceOffline,
                      ]}
                      accessibilityLabel={presence[item.userId]?.online ? 'Online' : 'Offline'}
                    />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.rowTitle}>{item.displayName || item.username}</Text>
                    <Text style={styles.rowSub}>
                      @{item.username} · {presence[item.userId]?.online ? 'Online' : 'Offline'}
                    </Text>
                  </View>
                </View>
              </Pressable>
              <Pressable style={[styles.smallButton, styles.buttonSecondary]} onPress={() => handleUnfriend(item)}>
                <Text style={styles.buttonText}>Unfriend</Text>
              </Pressable>
              <Pressable style={[styles.smallButton, styles.buttonDanger]} onPress={() => handleBlock(item)}>
                <Text style={styles.buttonText}>Block</Text>
              </Pressable>
            </View>
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
              </View>
            </View>
          ) : (
            <Text style={styles.hint}>Search a user to send a friend request.</Text>
          )}

          <View style={[styles.tabs, { marginTop: 16 }]}>
            {(['incoming', 'outgoing'] as const).map((key) => (
              <Pressable
                key={key}
                style={[styles.tab, requestTab === key && styles.tabActive]}
                onPress={() => setRequestTab(key)}
              >
                <Text style={[styles.tabText, requestTab === key && styles.tabTextActive]}>
                  {key === 'incoming' ? 'Incoming' : 'Sent'} ({requests[key].length})
                </Text>
              </Pressable>
            ))}
          </View>

          {requests[requestTab].length === 0 ? (
            <Text style={styles.hint}>
              {requestTab === 'incoming' ? 'No incoming requests.' : 'No pending sent requests.'}
            </Text>
          ) : (
            requests[requestTab].map((request) => {
              const userId = requestTab === 'incoming' ? request.fromUserId : request.toUserId;
              const person = people[userId];
              const name = person?.displayName || person?.username || `User ${userId.slice(0, 8)}`;
              return (
                <View key={`${request.fromUserId}-${request.toUserId}`} style={styles.friendRow}>
                  <View style={[styles.friendMain, { flex: 1 }]}>
                    <View style={styles.avatar}>
                      <Text style={styles.avatarText}>{getInitials(name)}</Text>
                    </View>
                    <Text style={[styles.rowTitle, { flex: 1 }]} numberOfLines={1}>
                      {name}
                    </Text>
                  </View>
                  {requestTab === 'incoming' ? (
                    <>
                      <Pressable
                        style={styles.smallButton}
                        disabled={busy}
                        onPress={() =>
                          void runRequestAction(
                            async () => {
                              const friend = await acceptFriendRequestApi(userId);
                              return friend;
                            },
                            `You are now friends with ${name}`,
                          )
                        }
                      >
                        <Text style={styles.buttonText}>Accept</Text>
                      </Pressable>
                      <Pressable
                        style={[styles.smallButton, styles.buttonSecondary]}
                        disabled={busy}
                        onPress={() =>
                          void runRequestAction(() => rejectFriendRequestApi(userId), 'Request rejected')
                        }
                      >
                        <Text style={styles.buttonText}>Reject</Text>
                      </Pressable>
                    </>
                  ) : (
                    <Pressable
                      style={[styles.smallButton, styles.buttonSecondary]}
                      disabled={busy}
                      onPress={() =>
                        void runRequestAction(() => cancelFriendRequestApi(userId), 'Request cancelled')
                      }
                    >
                      <Text style={styles.buttonText}>Cancel</Text>
                    </Pressable>
                  )}
                </View>
              );
            })
          )}
        </View>
      ) : null}

      {mode === 'profile' ? (
        <View style={styles.card}>
          <Text style={styles.label}>Display name</Text>
          <TextInput style={styles.input} value={displayName} onChangeText={setDisplayName} />
          <Text style={styles.label}>Avatar URL</Text>
          <TextInput
            style={styles.input}
            value={avatar}
            onChangeText={setAvatar}
            autoCapitalize="none"
            placeholder="https://… (empty = initials)"
          />
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
  buttonDanger: { backgroundColor: '#b91c1c' },
  smallButton: {
    backgroundColor: '#0068ff',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    alignItems: 'center',
  },
  friendRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
  },
  friendMain: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#0068ff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { color: '#fff', fontWeight: '700' },
  presenceDot: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    width: 12,
    height: 12,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#fff',
  },
  presenceOnline: { backgroundColor: '#22c55e' },
  presenceOffline: { backgroundColor: '#94a3b8' },
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
