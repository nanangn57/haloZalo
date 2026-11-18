
import { useNavigate } from 'react-router-dom'
import { useCallback, useEffect, useMemo, useState } from 'react'

import GroupHeader from '../components/group/GroupHeader'

import type { Group, Member } from '@core/types/group'

import { findUserApi } from '@core/userService'
import {
  getGroupsApi,
  createGroupApi,
  getGroupMembersApi,
  addGroupMemberApi,
  removeGroupMemberApi,
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

    setLoading(true)
    setLoadError('')

    try {
      const result = await getGroupsApi()
      setGroups(result)

      // Fetch member counts for all groups.
      // This uses the existing GET /groups/{groupId}/members endpoint.
      await Promise.all(
        result.map((group) => loadMemberCount(group.groupId)),
      )
    } catch (err) {
      setLoadError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách nhóm.',
      )
    } finally {
      setLoading(false)
    }
  }, [authUser?.userId, loadMemberCount])

  useEffect(() => {
    if (authUser?.userId) {
      void loadGroups()
    } else {
      setLoading(false)
    }
  }, [authUser?.userId, loadGroups])

  // Load members whenever selected group changes
  const loadGroupMembers = useCallback(async (groupId: string) => {
    setLoadingMembers(true)
    setMembersError('')

    try {
      const result = await getGroupMembersApi(groupId)

      setMembers(result.members)
      setMemberCounts((previous) => ({
        ...previous,
        [groupId]: result.members.length,
      }))
    } catch (err) {
      setMembers([])
      setMembersError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách thành viên.',
      )
    } finally {
      setLoadingMembers(false)
    }
  }, [])

  useEffect(() => {
    if (selectedGroup) {
      void loadGroupMembers(selectedGroup.groupId)
    } else {
      setMembers([])
      setMembersError('')
    }
  }, [selectedGroup?.groupId, loadGroupMembers])

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
      setActionError('Vui lòng nhập tên nhóm.')
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
      return
    }

    setSelectedGroup(group)

    setSearchQuery('')
    setFoundUser(null)
    setMemberError('')
    setMemberSuccess('')
    setMembers([])
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
        setMemberError('Bạn không thể thêm chính mình vào nhóm.')
        return
      }

      if (members.some((member) => member.userId === user.userId)) {
        setMemberError('Người dùng này đã là thành viên của nhóm.')
        return
      }

      setFoundUser(user)
    } catch (err) {
      setMemberError(
        err instanceof Error
          ? err.message
          : 'Không tìm thấy người dùng.',
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
        `Đã thêm ${foundUser.username} vào nhóm ${selectedGroup.name}.`,
      )

      setFoundUser(null)
      setSearchQuery('')

      await loadGroupMembers(selectedGroup.groupId)
    } catch (err) {
      setMemberError(
        err instanceof Error
          ? err.message
          : 'Không thể thêm thành viên.',
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
        ? `Bạn có chắc muốn rời nhóm "${selectedGroup.name}" không?`
        : 'Bạn có chắc muốn xoá thành viên này khỏi nhóm không?',
    )

    if (!confirmed) return

    setRemovingUserId(userId)
    setMemberError('')
    setMemberSuccess('')

    try {
      await removeGroupMemberApi(selectedGroup.groupId, userId)

      if (isLeavingSelf) {
        setSuccessMessage(`Bạn đã rời nhóm "${selectedGroup.name}".`)
        setSelectedGroup(null)
        setMembers([])
        await loadGroups()
      } else {
        setMemberSuccess('Đã xoá thành viên khỏi nhóm.')
        await loadGroupMembers(selectedGroup.groupId)
      }
    } catch (err) {
      setMemberError(
        err instanceof Error
          ? err.message
          : 'Không thể thực hiện thao tác.',
      )
    } finally {
      setRemovingUserId(null)
    }
  }

  // No authenticated session
  if (!authUser?.userId) {
    return (
      <div className="container vh-100 d-flex flex-column justify-content-center align-items-center">
        <h4>Vui lòng đăng nhập</h4>
        <p className="text-secondary">
          Bạn cần đăng nhập để sử dụng chức năng nhóm.
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
                  : 'You are a member'}
              </small>
            </div>

            <button
              type="button"
              className="btn btn-outline-secondary btn-sm"
              onClick={() => setSelectedGroup(null)}
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
                  onClick={() =>
                    void loadGroupMembers(selectedGroup.groupId)
                  }
                >
                  Retry
                </button>
              </div>
            ) : (
              <ul className="list-group">
                {members.map((member) => {
                  const isCurrentUser =
                    member.userId === authUser.userId

                  const canRemove =
                    selectedGroup.role === 'OWNER' &&
                    !isCurrentUser &&
                    member.role !== 'OWNER'

                  return (
                    <li
                      key={member.userId}
                      className="list-group-item d-flex justify-content-between align-items-center"
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
          onClick={() => void loadGroups()}
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
            onClick={() => void loadGroups()}
          >
            Try again
          </button>
        </div>
      ) : (
        <ul className="list-group list-group-flush flex-grow-1 overflow-auto">
          {filteredGroups.length === 0 ? (
            <li className="list-group-item text-center py-5">
              <i className="bi bi-people fs-1 text-secondary" />
              <h5 className="mt-3">
                {selectedFilter === 'owned'
                  ? 'You do not own any groups'
                  : 'No groups yet'}
              </h5>
              <p className="text-secondary">
                Create a group to start connecting with others.
              </p>

              {selectedFilter === 'all' && (
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => {
                    setActionError('')
                    setShowCreateForm(true)
                  }}
                >
                  <i className="bi bi-plus-lg me-2" />
                  Create group
                </button>
              )}
            </li>
          ) : (
            filteredGroups.map((group) => {
              const isSelected =
                selectedGroup?.groupId === group.groupId

              const count = memberCounts[group.groupId]

              return (
                <li
                  key={group.groupId}
                  className={`list-group-item p-0 ${
                    isSelected ? 'bg-primary-subtle' : ''
                  }`}
                >
                  <div className="d-flex align-items-center">
                    {/* Click group information to expand members */}
                    <button
                      type="button"
                      className="btn btn-light flex-grow-1 rounded-0 text-start p-3 border-0"
                      onClick={() => handleSelectGroup(group)}
                    >
                      <div className="d-flex align-items-center gap-3">
                        <div
                          className="rounded-circle bg-primary-subtle text-primary d-flex align-items-center justify-content-center"
                          style={{
                            width: 48,
                            height: 48,
                            flexShrink: 0,
                          }}
                        >
                          <i className="bi bi-people fs-4" />
                        </div>

                        <div className="flex-grow-1 overflow-hidden">
                          <div className="fw-semibold text-truncate">
                            {group.name}
                          </div>

                          <small className="text-secondary">
                            {group.role === 'OWNER' ? 'Owner' : 'Member'}
                            {' · '}
                            {count === undefined
                              ? 'Loading members...'
                              : count === null
                                ? 'Members unavailable'
                                : `${count} members`}
                          </small>
                        </div>

                        <i
                          className={`bi ${
                            isSelected
                              ? 'bi-chevron-up'
                              : 'bi-chevron-down'
                          } text-secondary`}
                        />
                      </div>
                    </button>

                    {/* Arrow opens ChatPage */}
                    <button
                      type="button"
                      className="btn btn-light rounded-0 border-0 align-self-stretch px-4"
                      title={`Open chat ${group.name}`}
                      aria-label={`Open chat ${group.name}`}
                      onClick={() => handleOpenChat(group)}
                    >
                      <i className="bi bi-chevron-right text-secondary fs-5" />
                    </button>
                  </div>
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
