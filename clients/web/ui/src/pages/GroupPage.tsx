
import { useNavigate } from 'react-router-dom'
import { useCallback, useEffect, useMemo, useState } from 'react'

import GroupHeader from '../components/group/GroupHeader'

import type { Group, Member } from '@core/types/group'

import { findUserApi, lookupUsersApi } from '@core/userService'
import {
  getGroupsApi,
  createGroupApi,
  getGroupMembersApi,
  addGroupMemberApi,
  removeGroupMemberApi,
  transferGroupOwnerApi,
  dissolveGroupApi,
  updateGroupApi,
} from '@core/groupService'
import { listFriendsApi, type Friend } from '@core/friendService'
import { resolveGroupDisplayTitle } from '@core/groupTitle'

import { getAuth } from '@core/authSession'
import { getInitials } from '@core/avatar'

async function memberDisplayNames(members: Member[]): Promise<string[]> {
  // Owner + mọi member (không bỏ bản thân).
  if (members.length === 0) {
    return []
  }
  try {
    const users = await lookupUsersApi(members.map((member) => member.userId))
    const byId = Object.fromEntries(users.map((user) => [user.userId, user]))
    return members.map((member) => {
      const user = byId[member.userId]
      return (user?.displayName || user?.username || member.username || '').trim()
    }).filter(Boolean)
  } catch {
    return members.map((member) => member.username).filter(Boolean)
  }
}

function GroupPage() {
  const authUser = getAuth()?.user
  const navigate = useNavigate()

  // Groups
  const [groups, setGroups] = useState<Group[]>([])
  const [selectedFilter, setSelectedFilter] =
    useState<'all' | 'owned'>('all')

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  // Member counts + names for Zalo-style untitled groups
  const [memberCounts, setMemberCounts] = useState<
    Record<string, number | null>
  >({})
  const [memberNamesByGroupId, setMemberNamesByGroupId] = useState<
    Record<string, string[]>
  >({})

  // Create group
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [groupName, setGroupName] = useState('')
  const [createFriends, setCreateFriends] = useState<Friend[]>([])
  const [createMemberIds, setCreateMemberIds] = useState<string[]>([])
  const [loadingCreateFriends, setLoadingCreateFriends] = useState(false)
  const [creating, setCreating] = useState(false)
  const [actionError, setActionError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')

  // Selected group and members
  const [selectedGroup, setSelectedGroup] =
    useState<Group | null>(null)

  const [members, setMembers] = useState<Member[]>([])
  const [loadingMembers, setLoadingMembers] = useState(false)
  const [membersError, setMembersError] = useState('')
  const [removingUserId, setRemovingUserId] = useState<string | null>(null)
  const [transferringUserId, setTransferringUserId] = useState<string | null>(null)

  // Owner: edit name / dissolve
  const [editingName, setEditingName] = useState(false)
  const [editNameValue, setEditNameValue] = useState('')
  const [savingName, setSavingName] = useState(false)
  const [dissolving, setDissolving] = useState(false)

  // Find and add user
  const [searchQuery, setSearchQuery] = useState('')
  const [foundUser, setFoundUser] = useState<
    Awaited<ReturnType<typeof findUserApi>> | null
  >(null)

  const [searching, setSearching] = useState(false)
  const [addingMember, setAddingMember] = useState(false)

  const [memberError, setMemberError] = useState('')
  const [memberSuccess, setMemberSuccess] = useState('')

  const rememberGroupMembers = useCallback(
    async (groupId: string, members: Member[]) => {
      setMemberCounts((previous) => ({
        ...previous,
        [groupId]: members.length,
      }))
      const names = await memberDisplayNames(members)
      setMemberNamesByGroupId((previous) => ({
        ...previous,
        [groupId]: names,
      }))
    },
    [],
  )

  // Load member count using the existing members API
  const loadMemberCount = useCallback(async (groupId: string) => {
    try {
      const result = await getGroupMembersApi(groupId)
      await rememberGroupMembers(groupId, result.members)
    } catch {
      setMemberCounts((previous) => ({
        ...previous,
        [groupId]: null,
      }))
    }
  }, [rememberGroupMembers])

  function groupLabel(group: Group): string {
    return resolveGroupDisplayTitle({
      customName: group.name,
      memberNames: memberNamesByGroupId[group.groupId],
      fallback: 'Group',
    })
  }

  // Load groups
  const loadGroups = useCallback(async () => {
    if (!authUser?.userId) return

    try {
      const result = await getGroupsApi()
      setGroups(result)

      // Fetch member counts for all groups.
      await Promise.all(
        result.map((group) => loadMemberCount(group.groupId)),
      )
    } catch (err) {
      setLoadError(
        err instanceof Error
          ? err.message
          : 'Cannot load group member list',
      )
    } finally {
      setLoading(false)
    }
  }, [authUser?.userId, loadMemberCount])

  useEffect(() => {
    if (!authUser?.userId) return

    let cancelled = false

    async function fetchGroups() {
      try {
        const result = await getGroupsApi()

        if (cancelled) return

        setGroups(result)

        await Promise.all(
          result.map(async (group) => {
            try {
              const memberResult = await getGroupMembersApi(
                group.groupId,
              )

              if (cancelled) return

              await rememberGroupMembers(group.groupId, memberResult.members)
            } catch {
              if (cancelled) return

              setMemberCounts((previous) => ({
                ...previous,
                [group.groupId]: null,
              }))
            }
          }),
        )
      } catch (err) {
        if (cancelled) return

        setLoadError(
          err instanceof Error
            ? err.message
            : 'Cannot load group member list',
        )
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void fetchGroups()

    return () => {
      cancelled = true
    }
  }, [authUser?.userId, rememberGroupMembers])

  // Load members whenever selected group changes
  const loadGroupMembers = useCallback(async (groupId: string) => {
    try {
      const result = await getGroupMembersApi(groupId)

      setMembers(result.members)
      setMembersError('')
      await rememberGroupMembers(groupId, result.members)
    } catch (err) {
      setMembers([])

      setMembersError(
        err instanceof Error
          ? err.message
          : 'Cannot load group member list',
      )
    } finally {
      setLoadingMembers(false)
    }
  }, [rememberGroupMembers])

  const selectedGroupId = selectedGroup?.groupId

  useEffect(() => {
    if (!selectedGroupId) return

    let cancelled = false

    getGroupMembersApi(selectedGroupId)
      .then(async (result) => {
        if (cancelled) return

        setMembers(result.members)
        setMembersError('')
        await rememberGroupMembers(selectedGroupId, result.members)
      })
      .catch((err) => {
        if (cancelled) return

        setMembers([])

        setMembersError(
          err instanceof Error
            ? err.message
            : 'Cannot load group member list',
        )
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingMembers(false)
        }
      })

  return () => {
    cancelled = true
  }
}, [selectedGroupId, rememberGroupMembers])

  // Filter groups
  const filteredGroups = useMemo(() => {
    if (selectedFilter === 'owned') {
      return groups.filter((group) => group.role === 'OWNER')
    }

    return groups
  }, [groups, selectedFilter])

  async function openCreateForm() {
    setShowCreateForm(true)
    setGroupName('')
    setCreateMemberIds([])
    setActionError('')
    setSuccessMessage('')
    setLoadingCreateFriends(true)
    try {
      const friends = await listFriendsApi()
      setCreateFriends(friends)
    } catch (err) {
      setCreateFriends([])
      setActionError(
        err instanceof Error
          ? err.message
          : 'Không tải được danh sách bạn bè.',
      )
    } finally {
      setLoadingCreateFriends(false)
    }
  }

  function toggleCreateMember(userId: string) {
    setCreateMemberIds((previous) =>
      previous.includes(userId)
        ? previous.filter((id) => id !== userId)
        : [...previous, userId],
    )
  }

  // Create group
  async function handleCreateGroup(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (createMemberIds.length === 0) {
      setActionError('Chọn ít nhất một thành viên để tạo nhóm.')
      return
    }

    const name = groupName.trim()

    setCreating(true)
    setActionError('')
    setSuccessMessage('')

    try {
      const newGroup = await createGroupApi({
        ...(name ? { name } : {}),
        memberIds: createMemberIds,
      })

      setGroups((previous) => [
        newGroup,
        ...previous.filter(
          (group) => group.groupId !== newGroup.groupId,
        ),
      ])

      const ownerName =
        authUser?.displayName?.trim() || authUser?.username || ''
      const selectedNames = [
        ownerName,
        ...createFriends
          .filter((friend) => createMemberIds.includes(friend.userId))
          .map((friend) => friend.displayName?.trim() || friend.username),
      ].filter(Boolean)
      setMemberCounts((previous) => ({
        ...previous,
        [newGroup.groupId]: 1 + createMemberIds.length,
      }))
      setMemberNamesByGroupId((previous) => ({
        ...previous,
        [newGroup.groupId]: selectedNames,
      }))

      setGroupName('')
      setCreateMemberIds([])
      setShowCreateForm(false)
      setSelectedFilter('all')
      setSuccessMessage('Tạo nhóm thành công.')

      // Refresh count from backend to avoid assuming the initial count.
      void loadMemberCount(newGroup.groupId)
    } catch (err) {
      setActionError(
        err instanceof Error
          ? err.message
          : 'Không thể tạo nhóm.',
      )
    } finally {
      setCreating(false)
    }
  }

  // Select or collapse a group to show its members
  function handleSelectGroup(group: Group) {
    if (selectedGroup?.groupId === group.groupId) {
      setSelectedGroup(null)
      setMembers([])
      setLoadingMembers(false)
      setMembersError('')
      return
    }

    setSelectedGroup(group)

    // Prepare state before the member-loading Effect runs.
    setLoadingMembers(true)
    setMembersError('')
    setMembers([])

    setSearchQuery('')
    setFoundUser(null)
    setMemberError('')
    setMemberSuccess('')
  }

  // Navigate to ChatPage
  function handleOpenChat(group: Group) {
    navigate(`/contacts/groups/${group.groupId}/chat`, {
      state: {
        conversationId: group.groupId,
        conversationType: 'GROUP',
        title: groupLabel(group),
      },
    })
  }

  // Search user
  async function handleSearchUser(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (!searchQuery.trim()) return

    setSearching(true)
    setFoundUser(null)
    setMemberError('')
    setMemberSuccess('')

    try {
      const user = await findUserApi(searchQuery.trim())

      if (user.userId === authUser?.userId) {
        setMemberError('Cannot add yourself to group')
        return
      }

      if (members.some((member) => member.userId === user.userId)) {
        setMemberError('Already a group member')
        return
      }

      setFoundUser(user)
    } catch (err) {
      setMemberError(
        err instanceof Error
          ? err.message
          : 'User not found.',
      )
    } finally {
      setSearching(false)
    }
  }

  // Owner adds member
  async function handleAddMember() {
    if (!selectedGroup || !foundUser) return

    setAddingMember(true)
    setMemberError('')
    setMemberSuccess('')

    try {
      await addGroupMemberApi(
        selectedGroup.groupId,
        { userId: foundUser.userId },
      )

      setMemberSuccess(
        `Add ${foundUser.username} into group ${groupLabel(selectedGroup)}.`,
      )

      setFoundUser(null)
      setSearchQuery('')

      setLoadingMembers(true)
      await loadGroupMembers(selectedGroup.groupId)
    } catch (err) {
      setMemberError(
        err instanceof Error
          ? err.message
          : 'Cannot add member.',
      )
    } finally {
      setAddingMember(false)
    }
  }

  // Owner removes another member; member leaves the group
  async function handleRemoveMember(userId: string) {
    if (!selectedGroup || !authUser?.userId) return

    const isOwner = selectedGroup.role === 'OWNER'
    const isLeavingSelf = userId === authUser.userId

    if (isOwner && isLeavingSelf) return

    const confirmed = window.confirm(
      isLeavingSelf
        ? `Are you sure to leave "${groupLabel(selectedGroup)}"?`
        : 'Are you sure you want to remove this user?',
    )

    if (!confirmed) return

    setRemovingUserId(userId)
    setMemberError('')
    setMemberSuccess('')

    try {
      await removeGroupMemberApi(selectedGroup.groupId, userId)

      if (isLeavingSelf) {
        setSuccessMessage(`You've left "${groupLabel(selectedGroup)}".`)

        setSelectedGroup(null)
        setMembers([])
        setLoadingMembers(false)
        setMembersError('')

        setLoading(true)
        setLoadError('')
        await loadGroups()
      } else {
        setMemberSuccess('User deleted from group.')

        setLoadingMembers(true)
        await loadGroupMembers(selectedGroup.groupId)
      }
    } catch (err) {
      setMemberError(
        err instanceof Error
          ? err.message
          : 'Error',
      )
    } finally {
      setRemovingUserId(null)
    }
  }

  function handleRefreshGroups() {
    setLoading(true)
    setLoadError('')
    void loadGroups()
  }

  // Owner chuyển quyền owner cho một member. Sau đó mình thành MEMBER.
  async function handleTransferOwner(member: Member) {
    if (!selectedGroup || transferringUserId) return

    if (
      !window.confirm(
        `Transfer ownership of "${groupLabel(selectedGroup)}" to ${member.username}? You will become a member.`,
      )
    ) {
      return
    }

    setTransferringUserId(member.userId)
    setMemberError('')
    setMemberSuccess('')

    try {
      await transferGroupOwnerApi(selectedGroup.groupId, member.userId)
      setSelectedGroup({
        ...selectedGroup,
        ownerId: member.userId,
        role: 'MEMBER',
      })
      setGroups((previous) =>
        previous.map((group) =>
          group.groupId === selectedGroup.groupId
            ? { ...group, ownerId: member.userId, role: 'MEMBER' }
            : group,
        ),
      )
      setMemberSuccess(`${member.username} is now the owner.`)
      setLoadingMembers(true)
      await loadGroupMembers(selectedGroup.groupId)
    } catch (err) {
      setMemberError(
        err instanceof Error ? err.message : 'Cannot transfer ownership',
      )
    } finally {
      setTransferringUserId(null)
    }
  }

  // Owner sửa tên nhóm.
  async function handleSaveGroupName(event: React.FormEvent) {
    event.preventDefault()
    if (!selectedGroup || savingName) return

    const name = editNameValue.trim()
    if (name === (selectedGroup.name?.trim() || '')) {
      setEditingName(false)
      return
    }

    setSavingName(true)
    setMemberError('')
    setMemberSuccess('')

    try {
      // Rỗng = xoá tên đặt tay → chat hiện tên thành viên.
      const updated = await updateGroupApi(selectedGroup.groupId, { name })
      const next = { ...selectedGroup, ...updated }
      setSelectedGroup(next)
      setGroups((previous) =>
        previous.map((group) =>
          group.groupId === next.groupId ? { ...group, name: next.name } : group,
        ),
      )
      setEditingName(false)
      setMemberSuccess(
        name ? 'Group name updated.' : 'Name cleared — chat will show members.',
      )
    } catch (err) {
      setMemberError(
        err instanceof Error ? err.message : 'Cannot update group',
      )
    } finally {
      setSavingName(false)
    }
  }

  // Owner giải tán nhóm.
  async function handleDissolveGroup() {
    if (!selectedGroup || dissolving) return

    if (
      !window.confirm(
        `Dissolve "${groupLabel(selectedGroup)}"? This removes the group for everyone and cannot be undone.`,
      )
    ) {
      return
    }

    setDissolving(true)
    setMemberError('')
    setMemberSuccess('')

    try {
      await dissolveGroupApi(selectedGroup.groupId)
      setSuccessMessage(`Group "${groupLabel(selectedGroup)}" was dissolved.`)
      setSelectedGroup(null)
      setMembers([])
      setLoadingMembers(false)
      setMembersError('')
      setLoading(true)
      setLoadError('')
      await loadGroups()
    } catch (err) {
      setMemberError(
        err instanceof Error ? err.message : 'Cannot dissolve group',
      )
    } finally {
      setDissolving(false)
    }
  }

  // No authenticated session
  if (!authUser?.userId) {
    return (
      <div className="container vh-100 d-flex flex-column justify-content-center align-items-center">
        <h4>Please login</h4>
        <p className="text-secondary">
          You need to login to use Group function
        </p>
      </div>
    )
  }

  return (
    <main
      className="d-flex flex-column overflow-hidden"
      style={{ minWidth: 0 }}
    >
      <GroupHeader
        onCreateGroup={() => {
          void openCreateForm()
        }}
      />

      {/* Selected group and members */}
      {selectedGroup && (
        <div className="border-bottom p-3">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <div>
              {editingName ? (
                <form
                  className="d-flex gap-2 mb-1"
                  onSubmit={(event) => void handleSaveGroupName(event)}
                >
                  <input
                    className="form-control form-control-sm"
                    value={editNameValue}
                    maxLength={64}
                    autoFocus
                    disabled={savingName}
                    onChange={(event) => setEditNameValue(event.target.value)}
                    aria-label="Group name"
                  />
                  <button
                    type="submit"
                    className="btn btn-primary btn-sm"
                    disabled={savingName || !editNameValue.trim()}
                  >
                    {savingName ? 'Saving…' : 'Save'}
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    disabled={savingName}
                    onClick={() => setEditingName(false)}
                  >
                    Cancel
                  </button>
                </form>
              ) : (
                <h5 className="mb-1 d-flex align-items-center gap-2">
                  {groupLabel(selectedGroup)}
                  {selectedGroup.role === 'OWNER' && (
                    <button
                      type="button"
                      className="btn btn-link btn-sm p-0"
                      title="Edit group name"
                      aria-label="Edit group name"
                      onClick={() => {
                        setEditNameValue(selectedGroup.name)
                        setEditingName(true)
                      }}
                    >
                      <i className="bi bi-pencil" aria-hidden="true" />
                    </button>
                  )}
                </h5>
              )}
              <small className="text-secondary">
                {selectedGroup.role === 'OWNER'
                  ? 'You are the owner'
                  : 'You are a member'}
              </small>
            </div>

            <button
              type="button"
              className="btn btn-outline-secondary btn-sm"
              onClick={() => {
                setSelectedGroup(null)
                setMembers([])
                setLoadingMembers(false)
                setMembersError('')
                setEditingName(false)
              }}
            >
              Close
            </button>
          </div>

          {/* Member list */}
          <div className="mb-3">
            <h6>
              Members ({memberCounts[selectedGroup.groupId] ?? members.length})
            </h6>

            {loadingMembers ? (
              <p className="text-secondary mb-0">
                Loading members...
              </p>
            ) : membersError ? (
              <div className="alert alert-danger mb-0">
                {membersError}
                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger ms-2"
                  onClick={() => {
                    setLoadingMembers(true)
                    setMembersError('')
                    void loadGroupMembers(selectedGroup.groupId)
                  }}
                >
                  Retry
                </button>
              </div>
            ) : (
              <ul className="list-group">
                {members.map((member) => {
                  const isCurrentUser =
                    member.userId === authUser.userId

                  const isOwnerViewer = selectedGroup.role === 'OWNER'

                  const canRemove =
                    isOwnerViewer &&
                    !isCurrentUser &&
                    member.role !== 'OWNER'

                  const canTransfer =
                    isOwnerViewer &&
                    !isCurrentUser &&
                    member.role !== 'OWNER'

                  return (
                    <li
                      key={member.userId}
                      className="list-group-item d-flex justify-content-between align-items-center gap-2"
                    >
                      <div>
                        <span className="fw-semibold">
                          {member.username}
                        </span>

                        {isCurrentUser && (
                          <small className="text-secondary ms-2">
                            (You)
                          </small>
                        )}

                        <span className="badge bg-secondary ms-2">
                          {member.role}
                        </span>
                      </div>

                      <div className="d-flex gap-2 flex-wrap justify-content-end">
                        {canTransfer && (
                          <button
                            type="button"
                            className="btn btn-outline-primary btn-sm"
                            disabled={transferringUserId !== null}
                            onClick={() => void handleTransferOwner(member)}
                          >
                            {transferringUserId === member.userId
                              ? '…'
                              : 'Make owner'}
                          </button>
                        )}
                        {canRemove && (
                          <button
                            type="button"
                            className="btn btn-outline-danger btn-sm"
                            disabled={removingUserId !== null}
                            onClick={() =>
                              void handleRemoveMember(member.userId)
                            }
                          >
                            {removingUserId === member.userId
                              ? 'Removing...'
                              : 'Remove'}
                          </button>
                        )}
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>

          {/* Owner adds members */}
          {selectedGroup.role === 'OWNER' && (
            <>
              <form
                onSubmit={handleSearchUser}
                className="d-flex gap-2"
              >
                <input
                  type="text"
                  className="form-control"
                  placeholder="Enter username or email"
                  value={searchQuery}
                  onChange={(event) =>
                    setSearchQuery(event.target.value)
                  }
                  disabled={searching || addingMember}
                />

                <button
                  type="submit"
                  className="btn btn-primary text-nowrap"
                  disabled={
                    searching ||
                    addingMember ||
                    !searchQuery.trim()
                  }
                >
                  {searching ? 'Searching...' : 'Search'}
                </button>
              </form>

              {foundUser && (
                <div className="border rounded p-3 mt-3">
                  <div className="d-flex align-items-center justify-content-between gap-2">
                    <div className="overflow-hidden">
                      <div className="fw-semibold text-truncate">
                        {foundUser.displayName || foundUser.username}
                      </div>

                      <small className="text-secondary">
                        {foundUser.email}
                      </small>
                    </div>

                    <button
                      type="button"
                      className="btn btn-success btn-sm text-nowrap"
                      onClick={() => void handleAddMember()}
                      disabled={addingMember}
                    >
                      {addingMember ? 'Adding...' : 'Add'}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}

          {/* Member leaves group */}
          {selectedGroup.role === 'MEMBER' && (
            <button
              type="button"
              className="btn btn-outline-danger btn-sm mt-3"
              disabled={removingUserId !== null}
              onClick={() =>
                void handleRemoveMember(authUser.userId)
              }
            >
              {removingUserId === authUser.userId
                ? 'Leaving...'
                : 'Leave group'}
            </button>
          )}

          {selectedGroup.role === 'OWNER' && (
            <div className="mt-3 pt-3 border-top">
              <button
                type="button"
                className="btn btn-outline-danger btn-sm"
                disabled={dissolving}
                onClick={() => void handleDissolveGroup()}
              >
                {dissolving ? 'Dissolving…' : 'Dissolve group'}
              </button>
              <div className="form-text">
                Owners can leave only after transferring ownership, or dissolve the group.
              </div>
            </div>
          )}

          {memberError && (
            <div className="alert alert-danger mt-3 mb-0" role="alert">
              {memberError}
            </div>
          )}

          {memberSuccess && (
            <div className="alert alert-success mt-3 mb-0" role="status">
              {memberSuccess}
            </div>
          )}
        </div>
      )}

      {/* Create group form */}
      {showCreateForm && (
        <div className="border-bottom p-3">
          <form onSubmit={handleCreateGroup}>
            <label
              htmlFor="groupName"
              className="form-label fw-semibold"
            >
              Group name <span className="text-muted fw-normal">(optional)</span>
            </label>

            <input
              id="groupName"
              type="text"
              className="form-control mb-3"
              placeholder="Leave empty = member names"
              value={groupName}
              disabled={creating}
              onChange={(event) =>
                setGroupName(event.target.value)
              }
            />

            <div className="mb-2 fw-semibold">
              Members{' '}
              <span className="text-muted fw-normal">
                ({createMemberIds.length} selected)
              </span>
            </div>

            {loadingCreateFriends ? (
              <p className="text-muted small mb-3">Loading friends…</p>
            ) : createFriends.length === 0 ? (
              <p className="text-muted small mb-3">
                No friends yet. Add friends before creating a group.
              </p>
            ) : (
              <ul
                className="list-unstyled border rounded mb-3 p-2"
                style={{ maxHeight: 220, overflowY: 'auto' }}
              >
                {createFriends.map((friend) => {
                  const checked = createMemberIds.includes(friend.userId)
                  const label =
                    friend.displayName?.trim() || friend.username
                  return (
                    <li key={friend.userId} className="form-check py-1">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        id={`create-member-${friend.userId}`}
                        checked={checked}
                        disabled={creating}
                        onChange={() => toggleCreateMember(friend.userId)}
                      />
                      <label
                        className="form-check-label"
                        htmlFor={`create-member-${friend.userId}`}
                      >
                        {label}
                        {friend.displayName?.trim() ? (
                          <span className="text-muted"> · {friend.username}</span>
                        ) : null}
                      </label>
                    </li>
                  )
                })}
              </ul>
            )}

            <div className="d-flex gap-2">
              <button
                type="submit"
                className="btn btn-primary text-nowrap"
                disabled={
                  creating ||
                  loadingCreateFriends ||
                  createMemberIds.length === 0
                }
              >
                {creating ? 'Creating...' : 'Create'}
              </button>

              <button
                type="button"
                className="btn btn-outline-secondary"
                disabled={creating}
                onClick={() => {
                  setShowCreateForm(false)
                  setGroupName('')
                  setCreateMemberIds([])
                  setActionError('')
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {successMessage && (
        <div className="alert alert-success m-3" role="status">
          {successMessage}
        </div>
      )}

      {actionError && (
        <div className="alert alert-danger m-3" role="alert">
          {actionError}
        </div>
      )}

      {/* Group filter and refresh */}
      <div className="border-bottom p-3 d-flex align-items-center justify-content-between">
        <div className="btn-group" role="group">
          <button
            type="button"
            className={`btn ${
              selectedFilter === 'all'
                ? 'btn-primary'
                : 'btn-outline-primary'
            }`}
            onClick={() => setSelectedFilter('all')}
          >
            All groups
          </button>

          <button
            type="button"
            className={`btn ${
              selectedFilter === 'owned'
                ? 'btn-primary'
                : 'btn-outline-primary'
            }`}
            onClick={() => setSelectedFilter('owned')}
          >
            Groups I own
          </button>
        </div>

        <button
          type="button"
          className="btn btn-outline-secondary"
          onClick={handleRefreshGroups}
          disabled={loading}
          title="Refresh groups"
        >
          <i className="bi bi-arrow-clockwise me-1" />
          Refresh
        </button>
      </div>

      {/* Group list */}
      {loading ? (
        <div className="text-center p-4">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
          <p className="text-secondary mt-2">Loading groups...</p>
        </div>
      ) : loadError ? (
        <div className="text-center p-5">
          <i className="bi bi-exclamation-circle fs-1 text-danger" />
          <h5 className="mt-3">Unable to load groups</h5>
          <p className="text-danger">{loadError}</p>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleRefreshGroups}
          >
            Try again
          </button>
        </div>
      ) : (
        <ul className="group-list flex-grow-1 overflow-auto">
          {filteredGroups.length === 0 ? (
            <li className="app-empty-state">
              <i className="bi bi-people" style={{ fontSize: 40 }} />
              <h2 style={{ fontSize: 18, marginTop: 12 }}>
                {selectedFilter === 'owned'
                  ? 'You do not own any groups'
                  : 'No groups yet'}
              </h2>
              <p>
                Create a group to start connecting with others.
              </p>

              {selectedFilter === 'all' && (
                <button
                  type="button"
                  className="app-button app-button-primary"
                  onClick={() => {
                    void openCreateForm()
                  }}
                >
                  <i className="bi bi-plus-lg" aria-hidden="true" />
                  Create group
                </button>
              )}
            </li>
          ) : (
            filteredGroups.map((group) => {
              const isSelected =
                selectedGroup?.groupId === group.groupId

              const count = memberCounts[group.groupId]
              const label = groupLabel(group)
              const initial = getInitials(label, 1) || 'G'

              return (
                <li
                  key={group.groupId}
                  className={`group-row-item ${
                    isSelected ? 'is-selected' : ''
                  }`}
                >
                  <button
                    type="button"
                    className="group-row-main"
                    onClick={() => handleSelectGroup(group)}
                  >
                    <span className="group-avatar" aria-hidden="true">
                      {initial}
                    </span>

                    <span className="group-row-copy">
                      <span className="group-row-name">{label}</span>
                      <span className="group-row-meta">
                        {group.role === 'OWNER' ? 'Owner' : 'Member'}
                        {' · '}
                        {count === undefined
                          ? 'Loading members…'
                          : count === null
                            ? 'Members unavailable'
                            : `${count} members`}
                      </span>
                    </span>

                    <i
                      className={`bi ${
                        isSelected ? 'bi-chevron-up' : 'bi-chevron-down'
                      }`}
                      aria-hidden="true"
                      style={{ color: 'var(--app-text-muted)' }}
                    />
                  </button>

                  <button
                    type="button"
                    className="app-icon-button group-row-chat"
                    title={`Open chat ${label}`}
                    aria-label={`Open chat ${label}`}
                    onClick={() => handleOpenChat(group)}
                  >
                    <i className="bi bi-chat-dots" aria-hidden="true" />
                  </button>
                </li>
              )
            })
          )}
        </ul>
      )}
    </main>
  )
}

export default GroupPage
