import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TextInput,
  Pressable,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { getAuth } from '@core/authSession';
import {
  getGroupsApi,
  getGroupMembersApi,
  addGroupMemberApi,
  removeGroupMemberApi,
  transferGroupOwnerApi,
  dissolveGroupApi,
  updateGroupApi,
} from '@core/groupService';
import { findUserApi, lookupUsersApi } from '@core/userService';
import { resolveGroupDisplayTitle } from '@core/groupTitle';
import { confirmAction } from '@/lib/confirm';
import { createGroupConversationApi } from '@core/messageService';
import type { Group, Member } from '@core/types/group';

async function memberDisplayNames(members: Member[]): Promise<string[]> {
  // Owner + mọi member (không bỏ bản thân).
  if (members.length === 0) return [];
  try {
    const users = await lookupUsersApi(members.map((member) => member.userId));
    const byId = Object.fromEntries(users.map((user) => [user.userId, user]));
    return members
      .map((member) => {
        const user = byId[member.userId];
        return (user?.displayName || user?.username || member.username || '').trim();
      })
      .filter(Boolean);
  } catch {
    return members.map((member) => member.username).filter(Boolean);
  }
}

export default function GroupsPage() {
  const router = useRouter();
  const auth = getAuth();
  const currentUserId = auth?.user?.userId ?? '';

  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [memberQuery, setMemberQuery] = useState('');
  const [adding, setAdding] = useState(false);
  const [memberCount, setMemberCount] = useState<Record<string, number>>({});
  const [memberNamesByGroupId, setMemberNamesByGroupId] = useState<
    Record<string, string[]>
  >({});
  const [members, setMembers] = useState<Member[]>([]);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [transferringId, setTransferringId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [renaming, setRenaming] = useState(false);
  const [dissolving, setDissolving] = useState(false);

  const rememberGroupMembers = useCallback(
    async (groupId: string, nextMembers: Member[]) => {
      setMemberCount((previous) => ({ ...previous, [groupId]: nextMembers.length }));
      const names = await memberDisplayNames(nextMembers);
      setMemberNamesByGroupId((previous) => ({ ...previous, [groupId]: names }));
    },
    [],
  );

  const groupLabel = useCallback(
    (group: Group) =>
      resolveGroupDisplayTitle({
        customName: group.name,
        memberNames: memberNamesByGroupId[group.groupId],
        fallback: 'Group',
      }),
    [memberNamesByGroupId],
  );

  const dropGroup = useCallback((groupId: string) => {
    setGroups((previous) => previous.filter((group) => group.groupId !== groupId));
    setMemberCount((previous) => {
      const next = { ...previous };
      delete next[groupId];
      return next;
    });
    setMemberNamesByGroupId((previous) => {
      const next = { ...previous };
      delete next[groupId];
      return next;
    });
    setSelectedGroupId((current) => (current === groupId ? null : current));
  }, []);

  const load = useCallback(async (opts?: { silent?: boolean }) => {
    const silent = opts?.silent === true;
    try {
      if (!silent) {
        setLoading(true);
        setError('');
      }
      const list = await getGroupsApi();
      const ids = new Set(list.map((group) => group.groupId));
      setGroups(list);
      setSelectedGroupId((current) =>
        current && ids.has(current) ? current : null,
      );
      // Poll im lặng chỉ đồng bộ list (bắt dissolve từ máy khác).
      if (silent) return;
      await Promise.all(
        list.map(async (group) => {
          try {
            const result = await getGroupMembersApi(group.groupId);
            await rememberGroupMembers(group.groupId, result.members);
          } catch {
            dropGroup(group.groupId);
          }
        }),
      );
    } catch (err: unknown) {
      if (!silent) {
        setError(err instanceof Error ? err.message : 'Cannot load groups');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [dropGroup, rememberGroupMembers]);

  const loadMembers = useCallback(async (groupId: string) => {
    try {
      const result = await getGroupMembersApi(groupId);
      setMembers(result.members);
      await rememberGroupMembers(groupId, result.members);
    } catch {
      dropGroup(groupId);
      setError('');
    }
  }, [dropGroup, rememberGroupMembers]);

  useEffect(() => {
    if (!auth?.accessToken) {
      router.replace('/login');
    }
  }, [auth?.accessToken, router]);

  useFocusEffect(
    useCallback(() => {
      if (!auth?.accessToken) return undefined;
      void load();
      const timer = setInterval(() => void load({ silent: true }), 15_000);
      return () => clearInterval(timer);
    }, [auth?.accessToken, load]),
  );

  useEffect(() => {
    if (selectedGroupId) {
      void loadMembers(selectedGroupId);
    } else {
      setMembers([]);
    }
  }, [selectedGroupId, loadMembers]);

  const handleAddMember = async () => {
    if (!selectedGroupId || !memberQuery.trim() || adding) return;
    setAdding(true);
    setError('');
    try {
      const user = await findUserApi(memberQuery.trim());
      await addGroupMemberApi(selectedGroupId, { userId: user.userId });
      setMemberQuery('');
      await load();
      await loadMembers(selectedGroupId);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot add member');
    } finally {
      setAdding(false);
    }
  };

  const handleRemoveMember = async (userId: string) => {
    if (!selectedGroupId || removingId) return;
    setRemovingId(userId);
    setError('');
    try {
      await removeGroupMemberApi(selectedGroupId, userId);
      if (userId === currentUserId) {
        setSelectedGroupId(null);
        setMembers([]);
      } else {
        await loadMembers(selectedGroupId);
      }
      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot remove member');
    } finally {
      setRemovingId(null);
    }
  };

  const handleTransferOwner = (member: Member) => {
    if (!selectedGroupId || transferringId) return;
    confirmAction(
      'Transfer ownership',
      `Make ${member.username} the owner? You will become a member.`,
      () => {
        void (async () => {
          setTransferringId(member.userId);
          setError('');
          try {
            await transferGroupOwnerApi(selectedGroupId, member.userId);
            await loadMembers(selectedGroupId);
            await load();
          } catch (err: unknown) {
            setError(err instanceof Error ? err.message : 'Cannot transfer ownership');
          } finally {
            setTransferringId(null);
          }
        })();
      },
    );
  };

  const handleRename = async () => {
    if (!selectedGroupId || renaming) return;
    const name = renameValue.trim();
    if (name === (selectedGroup?.name?.trim() || '')) return;
    setRenaming(true);
    setError('');
    try {
      // Rỗng = xoá tên đặt tay → chat hiện tên thành viên.
      await updateGroupApi(selectedGroupId, { name });
      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot rename group');
    } finally {
      setRenaming(false);
    }
  };

  const handleDissolve = () => {
    if (!selectedGroupId || dissolving) return;
    confirmAction(
      'Dissolve group',
      'This removes the group for everyone and cannot be undone.',
      () => {
        void (async () => {
          setDissolving(true);
          setError('');
          try {
            await dissolveGroupApi(selectedGroupId);
            setSelectedGroupId(null);
            setMembers([]);
            await load();
          } catch (err: unknown) {
            setError(err instanceof Error ? err.message : 'Cannot dissolve group');
          } finally {
            setDissolving(false);
          }
        })();
      },
    );
  };

  const openChat = async (group: Group) => {
    try {
      await createGroupConversationApi(group.groupId);
      router.push({
        pathname: '/(tabs)/chat',
        params: {
          conversationId: group.groupId,
          conversationType: 'GROUP',
          title: groupLabel(group),
        },
      });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot open group chat');
    }
  };

  const selectedGroup = groups.find((group) => group.groupId === selectedGroupId);
  const isOwner = selectedGroup?.role === 'OWNER';

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Groups</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}

      {selectedGroupId ? (
        <View style={styles.panel}>
          <View style={styles.panelHeader}>
            <Text style={styles.panelTitle}>
              Manage · {selectedGroup ? groupLabel(selectedGroup) : selectedGroupId.slice(0, 8)}
            </Text>
            <Pressable onPress={() => setSelectedGroupId(null)}>
              <Text style={styles.closeText}>Close</Text>
            </Pressable>
          </View>
          {isOwner ? (
            <View style={styles.row}>
              <TextInput
                style={styles.input}
                placeholder="Name (empty = member names)"
                value={renameValue}
                onChangeText={setRenameValue}
                maxLength={64}
              />
              <Pressable
                style={styles.button}
                onPress={() => void handleRename()}
                disabled={renaming}
              >
                <Text style={styles.buttonText}>{renaming ? '…' : 'Save'}</Text>
              </Pressable>
            </View>
          ) : null}
          {isOwner ? (
            <View style={styles.row}>
              <TextInput
                style={styles.input}
                placeholder="Add member (username/email)"
                value={memberQuery}
                onChangeText={setMemberQuery}
              />
              <Pressable
                style={styles.button}
                onPress={() => void handleAddMember()}
                disabled={adding}
              >
                <Text style={styles.buttonText}>{adding ? '…' : 'Add'}</Text>
              </Pressable>
            </View>
          ) : null}
          {members.map((member) => {
            const canRemove =
              (isOwner && member.userId !== currentUserId && member.role !== 'OWNER') ||
              (!isOwner && member.userId === currentUserId);
            const canTransfer =
              isOwner && member.userId !== currentUserId && member.role !== 'OWNER';

            return (
              <View key={member.userId} style={styles.memberRow}>
                <Text style={styles.memberName}>
                  {member.username} · {member.role}
                </Text>
                <View style={styles.memberActions}>
                  {canTransfer ? (
                    <Pressable
                      style={styles.secondaryButton}
                      disabled={transferringId === member.userId}
                      onPress={() => handleTransferOwner(member)}
                    >
                      <Text style={styles.buttonText}>Owner</Text>
                    </Pressable>
                  ) : null}
                  {canRemove && member.role !== 'OWNER' ? (
                    <Pressable
                      style={styles.dangerButton}
                      disabled={removingId === member.userId}
                      onPress={() => void handleRemoveMember(member.userId)}
                    >
                      <Text style={styles.buttonText}>
                        {member.userId === currentUserId ? 'Leave' : 'Remove'}
                      </Text>
                    </Pressable>
                  ) : null}
                </View>
              </View>
            );
          })}
          {isOwner ? (
            <Pressable
              style={[styles.dangerButton, { alignSelf: 'flex-start', marginTop: 8 }]}
              disabled={dissolving}
              onPress={handleDissolve}
            >
              <Text style={styles.buttonText}>{dissolving ? '…' : 'Dissolve group'}</Text>
            </Pressable>
          ) : null}
          {selectedGroup?.role === 'MEMBER' ? (
            <Pressable
              style={[styles.dangerButton, { alignSelf: 'flex-start', marginTop: 8 }]}
              onPress={() => void handleRemoveMember(currentUserId)}
            >
              <Text style={styles.buttonText}>Leave group</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}

      {loading ? (
        <ActivityIndicator style={{ marginTop: 24 }} color="#0068ff" />
      ) : (
        <FlatList
          data={groups}
          keyExtractor={(item) => item.groupId}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                setRefreshing(true);
                void load().finally(() => setRefreshing(false));
              }}
              tintColor="#0068ff"
            />
          }
          ListEmptyComponent={<Text style={styles.empty}>No groups yet.</Text>}
          renderItem={({ item }) => {
            const selected = selectedGroupId === item.groupId;
            return (
              <View style={[styles.card, selected ? styles.cardSelected : null]}>
                <Pressable
                  onPress={() => {
                    setSelectedGroupId(item.groupId);
                    setRenameValue(item.name ?? '');
                    setMemberQuery('');
                    setError('');
                  }}
                  style={styles.cardBody}
                >
                  <Text style={styles.groupName}>{groupLabel(item)}</Text>
                  <Text style={styles.meta}>
                    {memberCount[item.groupId] ?? '?'} members · {item.role}
                  </Text>
                </Pressable>
                <Pressable style={styles.chatButton} onPress={() => void openChat(item)}>
                  <Text style={styles.buttonText}>Chat</Text>
                </Pressable>
              </View>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 16 },
  title: { fontSize: 22, fontWeight: '700', marginBottom: 12 },
  error: { color: '#b91c1c', marginBottom: 8 },
  row: { flexDirection: 'row', gap: 8, marginBottom: 10 },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  button: {
    backgroundColor: '#0068ff',
    borderRadius: 8,
    paddingHorizontal: 14,
    justifyContent: 'center',
  },
  dangerButton: {
    backgroundColor: '#b91c1c',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  buttonText: { color: '#fff', fontWeight: '600' },
  empty: { color: '#64748b', marginTop: 24, textAlign: 'center' },
  card: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 10,
    padding: 12,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  cardSelected: {
    borderColor: '#0068ff',
    backgroundColor: '#eff6ff',
  },
  cardBody: { flex: 1 },
  groupName: { fontSize: 16, fontWeight: '600' },
  meta: { color: '#64748b', marginTop: 4 },
  chatButton: {
    backgroundColor: '#0068ff',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  panel: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
  },
  panelHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
    gap: 8,
  },
  panelTitle: { fontWeight: '700', flex: 1 },
  closeText: { color: '#0068ff', fontWeight: '600' },
  memberRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    gap: 8,
  },
  memberName: { color: '#334155', flex: 1 },
  memberActions: { flexDirection: 'row', gap: 6, alignItems: 'center' },
  secondaryButton: {
    backgroundColor: '#0068ff',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
});
