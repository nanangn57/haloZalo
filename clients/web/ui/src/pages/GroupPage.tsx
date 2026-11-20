
import { useNavigate } from 'react-router-dom'
import { useCallback, useEffect, useMemo, useState } from 'react'

import GroupHeader from '../components/group/GroupHeader'

import type { AssignableGroupRole, Group, Member } from '@core/types/group'

import { findUserApi } from '@core/userService'
import {
  getGroupsApi,
  createGroupApi,
  getGroupMembersApi,
  addGroupMemberApi,
  removeGroupMemberApi,
  assignGroupMemberRoleApi,
} from '@core/groupService'

import { getAuth } from '@core/authSession'

function GroupPage() {
  const authUser = getAuth()?.user
  const navigate = useNavigate()

  // Groups
  const [groups, setGroups] = useState<Group[]>([])
  const [selectedFilter, setSelectedFilter] =
    useState<'all' | 'owned'>('all')

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  // Member counts
  const [memberCounts, setMemberCounts] = useState<
    Record<string, number | null>
  >({})

  // Create group
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [groupName, setGroupName] = useState('')
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
  const [roleUpdatingUserId, setRoleUpdatingUserId] = useState<string | null>(null)

  // Find and add user
  const [searchQuery, setSearchQuery] = useState('')
  const [foundUser, setFoundUser] = useState<
    Awaited<ReturnType<typeof findUserApi>> | null
  >(null)

  const [searching, setSearching] = useState(false)
  const [addingMember, setAddingMember] = useState(false)

  const [memberError, setMemberError] = useState('')
  const [memberSuccess, setMemberSuccess] = useState('')

  // Load member count using the existing members API
  const loadMemberCount = useCallback(async (groupId: string) => {
    try {
      const result = await getGroupMembersApi(groupId)

      setMemberCounts((previous) => ({
        ...previous,
        [groupId]: result.members.length,
      }))
    } catch {
      setMemberCounts((previous) => ({
        ...previous,
        [groupId]: null,
      }))
    }
  }, [])

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

              setMemberCounts((previous) => ({
                ...previous,
                [group.groupId]: memberResult.members.length,
              }))
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
  }, [authUser?.userId])

  // Load members whenever selected group changes
  const loadGroupMembers = useCallback(async (groupId: string) => {
    try {
      const result = await getGroupMembersApi(groupId)

      setMembers(result.members)
      setMembersError('')

      setMemberCounts((previous) => ({
        ...previous,
        [groupId]: result.members.length,
      }))
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
  }, [])

  const selectedGroupId = selectedGroup?.groupId

  useEffect(() => {
    if (!selectedGroupId) return

    let cancelled = false

    getGroupMembersApi(selectedGroupId)
      .then((result) => {
        if (cancelled) return

        setMembers(result.members)
        setMembersError('')

        setMemberCounts((previous) => ({
          ...previous,
          [selectedGroupId]: result.members.length,
        }))
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
}, [selectedGroupId])

  // Filter groups
  const filteredGroups = useMemo(() => {
    if (selectedFilter === 'owned') {
      return groups.filter((group) => group.role === 'OWNER')
    }

    return groups
  }, [groups, selectedFilter])

  // Create group
  async function handleCreateGroup(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const name = groupName.trim()

    if (!name) {
      setActionError('Please enter group name')
      return
    }

    setCreating(true)
    setActionError('')
    setSuccessMessage('')

    try {
      const newGroup = await createGroupApi({ name })

      setGroups((previous) => [
        newGroup,
        ...previous.filter(
          (group) => group.groupId !== newGroup.groupId,
        ),
      ])

      setMemberCounts((previous) => ({
        ...previous,
        [newGroup.groupId]: 1,
      }))

      setGroupName('')
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
        title: group.name,
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
        `Add ${foundUser.username} into group ${selectedGroup.name}.`,
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
        ? `Are you sure to leave "${selectedGroup.name}"?`
        : 'Are you sure you want to remove this user?',
    )

    if (!confirmed) return

    setRemovingUserId(userId)
    setMemberError('')
    setMemberSuccess('')

    try {
      await removeGroupMemberApi(selectedGroup.groupId, userId)

      if (isLeavingSelf) {
        setSuccessMessage(`You've left "${selectedGroup.name}".`)

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

  async function handleAssignRole(
    userId: string,
    role: AssignableGroupRole,
  ) {
    if (!selectedGroup || roleUpdatingUserId) return

    setRoleUpdatingUserId(userId)
    setMemberError('')
    setMemberSuccess('')

    try {
      await assignGroupMemberRoleApi(selectedGroup.groupId, userId, role)
      setMemberSuccess(
        role === 'ADMIN' ? 'Member promoted to ADMIN.' : 'Member set to MEMBER.',
      )
      setLoadingMembers(true)
      await loadGroupMembers(selectedGroup.groupId)
    } catch (err) {
      setMemberError(
        err instanceof Error ? err.message : 'Cannot update role',
      )
    } finally {
      setRoleUpdatingUserId(null)
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
          setActionError('')
          setSuccessMessage('')
          setShowCreateForm(true)
        }}
      />

      {/* Selected group and members */}
      {selectedGroup && (
        <div className="border-bottom p-3">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <div>
              <h5 className="mb-1">{selectedGroup.name}</h5>
              <small className="text-secondary">
                {selectedGroup.role === 'OWNER'
                  ? 'You are the owner'
                  : selectedGroup.role === 'ADMIN'
                    ? 'You are an admin'
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
              }
              }
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

                  const canManageMembers =
                    selectedGroup.role === 'OWNER' ||
                    selectedGroup.role === 'ADMIN'

                  const canRemove =
                    canManageMembers &&
                    !isCurrentUser &&
                    member.role !== 'OWNER' &&
                    !(selectedGroup.role === 'ADMIN' && member.role === 'ADMIN')

                  const canAssignRole =
                    selectedGroup.role === 'OWNER' &&
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
                        {canAssignRole && member.role !== 'ADMIN' && (
                          <button
                            type="button"
                            className="btn btn-outline-primary btn-sm"
                            disabled={roleUpdatingUserId !== null}
                            onClick={() =>
                              void handleAssignRole(member.userId, 'ADMIN')
                            }
                          >
                            {roleUpdatingUserId === member.userId
                              ? '…'
                              : 'Make admin'}
                          </button>
                        )}
                        {canAssignRole && member.role === 'ADMIN' && (
                          <button
                            type="button"
                            className="btn btn-outline-secondary btn-sm"
                            disabled={roleUpdatingUserId !== null}
                            onClick={() =>
                              void handleAssignRole(member.userId, 'MEMBER')
                            }
                          >
                            {roleUpdatingUserId === member.userId
                              ? '…'
                              : 'Make member'}
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

          {/* Owner / admin adds members */}
          {(selectedGroup.role === 'OWNER' ||
            selectedGroup.role === 'ADMIN') && (
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

          {/* Member / admin leaves group */}
          {(selectedGroup.role === 'MEMBER' ||
            selectedGroup.role === 'ADMIN') && (
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
              Group name
            </label>

            <div className="d-flex gap-2">
              <input
                id="groupName"
                type="text"
                className="form-control"
                placeholder="Enter group name"
                value={groupName}
                disabled={creating}
                onChange={(event) =>
                  setGroupName(event.target.value)
                }
              />

              <button
                type="submit"
                className="btn btn-primary text-nowrap"
                disabled={creating || !groupName.trim()}
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
                    setActionError('')
                    setShowCreateForm(true)
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
              const initial = (group.name?.trim()?.charAt(0) || 'G').toUpperCase()

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
                      <span className="group-row-name">{group.name}</span>
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
                    title={`Open chat ${group.name}`}
                    aria-label={`Open chat ${group.name}`}
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
