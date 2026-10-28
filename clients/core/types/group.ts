export type GroupRole = 'OWNER' | 'MEMBER'

export interface Group {
  groupId: string
  name: string
  ownerId: string
  role: GroupRole
}

export interface GroupListResponse {
  groups: Group[]
}

export interface CreateGroupRequest {
  name: string
}

export interface AddMemberRequest {
  userId: string
}

export interface Membership {
  groupId: string
  userId: string
  role: 'MEMBER'
}
