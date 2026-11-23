/**
 * Chữ cái viết tắt cho avatar mặc định (decisions.md: không có URL thì hiện chữ cái).
 * "Ngọc Anh" → "NA", "huy" → "H", rỗng → "?".
 */
export function getInitials(name: string | null | undefined, maxLength = 2): string {
  const words = (name ?? '')
    .trim()
    .replace(/[@_.\-]+/g, ' ')
    .split(/\s+/)
    .filter(Boolean)

  if (words.length === 0) {
    return '?'
  }

  const letters =
    words.length === 1
      ? Array.from(words[0]).slice(0, maxLength)
      : words.slice(0, maxLength).map((word) => Array.from(word)[0])

  return letters.join('').toUpperCase()
}

/** Màu nền ổn định theo tên, dùng cho avatar chữ cái. */
const AVATAR_COLORS = [
  '#0d6efd', '#6f42c1', '#d63384', '#fd7e14',
  '#198754', '#20c997', '#0dcaf0', '#6610f2',
]

export function getAvatarColor(seed: string | null | undefined): string {
  const text = seed ?? ''
  let hash = 0
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) >>> 0
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}
