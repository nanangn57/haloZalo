export type GroupRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export type AssignableGroupRole = 'ADMIN' | 'MEMBER'

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
  role: GroupRole
}

export interface Member {
  userId: string
  username: string
  role: GroupRole
}

export interface SetRoleRequest {
  role: AssignableGroupRole
}

export interface MemberListResponse {
  members: Member[]
}