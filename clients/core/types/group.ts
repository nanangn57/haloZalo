// Chỉ hai role (decisions.md): không còn ADMIN nhóm.
export type GroupRole = 'OWNER' | 'MEMBER'

export interface Group {
  groupId: string
  name: string
  ownerId: string
  role: GroupRole
  description?: string
  avatar?: string
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

export interface UpdateGroupRequest {
  name?: string
  description?: string
  avatar?: string
}

export interface TransferOwnerRequest {
  userId: string
}

export interface MemberListResponse {
  members: Member[]
}