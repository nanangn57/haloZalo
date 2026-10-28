
import { useEffect, useMemo, useState } from 'react'

import ContactSidebar from '../components/contact/ContactSidebar'
import NavBar from '../components/nav/NavBar'
import GroupHeader from '../components/group/GroupHeader'
import GroupListItem from '../components/group/GroupListItem'

import type { Group } from '../types/group'
import type { User } from '../types/user'

import {
  getGroupsApi,
  createGroupApi,
} from '../../core/groupService'

function GroupPage() {
  const currentUser: User = {
    id: '1',
    name: 'Khanh',
    avatar: 'https://i.pravatar.cc/150?img=10',
    online: true,
    lastSeen: 1790421180,
    unread: 0,
  }

  const [groups, setGroups] = useState<Group[]>([])
  const [selectedFilter, setSelectedFilter] =
    useState<'all' | 'owned'>('all')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showCreateForm, setShowCreateForm] = useState(false)
  const [groupName, setGroupName] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    async function loadGroups() {
      setLoading(true)
      setError('')

      try {
        const result = await getGroupsApi()
        setGroups(result)
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : 'Không thể tải danh sách nhóm.',
        )
      } finally {
        setLoading(false)
      }
    }

    void loadGroups()
  }, [])

  const filteredGroups = useMemo(() => {
    if (selectedFilter === 'owned') {
      return groups.filter((group) => group.role === 'OWNER')
    }

    return groups
  }, [groups, selectedFilter])

  async function handleCreateGroup(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const name = groupName.trim()

    if (!name) {
      setError('Vui lòng nhập tên nhóm.')
      return
    }

    setCreating(true)
    setError('')

    try {
      const newGroup = await createGroupApi({ name })

      setGroups((previous) => [...previous, newGroup])
      setGroupName('')
      setShowCreateForm(false)
      setSelectedFilter('all')
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tạo nhóm.',
      )
    } finally {
      setCreating(false)
    }
  }

  function handleSelectGroup(group: Group) {
    // TODO: Điều hướng đến trang chat/chi tiết nhóm.
    console.log('Selected group:', group)
  }

  return (
    <div className="d-flex w-100 vh-100 overflow-hidden">
      <NavBar currentUser={currentUser} />

      <div className="flex-shrink-0">
        <ContactSidebar />
      </div>

      <main
        className="flex-grow-1 d-flex flex-column overflow-hidden"
        style={{ minWidth: 0, width: 0 }}
      >
        <GroupHeader
          onCreateGroup={() => {
            setError('')
            setShowCreateForm(true)
          }}
        />

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
                  maxLength={64}
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
                    setError('')
                  }}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {error && (
          <div className="alert alert-danger m-3" role="alert">
            {error}
          </div>
        )}

        <div className="border-bottom p-3">
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
        </div>

        {loading ? (
          <div className="text-center p-4">
            <div className="spinner-border text-primary" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
            <p className="text-secondary mt-2">Loading groups...</p>
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
                    onClick={() => setShowCreateForm(true)}
                  >
                    <i className="bi bi-plus-lg me-2" />
                    Create group
                  </button>
                )}
              </li>
            ) : (
              filteredGroups.map((group) => (
                <GroupListItem
                  key={group.groupId}
                  group={group}
                  onSelect={handleSelectGroup}
                />
              ))
            )}
          </ul>
        )}
      </main>
    </div>
  )
}

export default GroupPage
