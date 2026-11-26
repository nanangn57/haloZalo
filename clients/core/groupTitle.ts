/**
 * Tiêu đề nhóm kiểu Zalo: tên đặt tay nếu có, không thì ghép tên
 * owner + mọi thành viên. Nhiều người → tối đa `maxNames` tên rồi thêm "…".
 */
export function formatMemberGroupTitle(
  names: string[],
  maxNames = 3,
): string {
  const cleaned = names
    .map((name) => name.trim())
    .filter(Boolean)
  if (cleaned.length === 0) {
    return ''
  }
  if (cleaned.length <= maxNames) {
    return cleaned.join(', ')
  }
  return `${cleaned.slice(0, maxNames).join(', ')}…`
}

export function resolveGroupDisplayTitle(options: {
  customName?: string | null
  memberNames?: string[]
  /** Khi chưa có tên đặt tay và chưa resolve được thành viên. */
  fallback?: string
}): string {
  const custom = options.customName?.trim()
  if (custom) {
    return custom
  }
  const fromMembers = formatMemberGroupTitle(options.memberNames ?? [])
  if (fromMembers) {
    return fromMembers
  }
  return options.fallback ?? 'Group'
}
