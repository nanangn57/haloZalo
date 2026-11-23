import { getAvatarColor, getInitials } from '@core/avatar'

interface AvatarProps {
  name: string
  /** URL ảnh. Rỗng hoặc lỗi tải thì hiện chữ cái viết tắt. */
  src?: string | null
  size?: number
  className?: string
  /** Chữ cái đã tính sẵn (ví dụ avatar nhóm). Mặc định lấy từ `name`. */
  initials?: string
}

// Avatar dùng chung: ảnh khi có URL, ngược lại chữ cái viết tắt (decisions.md).
function Avatar({ name, src, size = 48, className = '', initials }: AvatarProps) {
  const mark = (initials || getInitials(name)).slice(0, 2)

  if (src) {
    return (
      <img
        src={src}
        alt=""
        className={`rounded-circle flex-shrink-0 ${className}`}
        width={size}
        height={size}
        style={{ objectFit: 'cover' }}
      />
    )
  }

  return (
    <span
      className={`rounded-circle flex-shrink-0 d-inline-flex align-items-center justify-content-center fw-bold ${className}`}
      style={{
        width: size,
        height: size,
        backgroundColor: getAvatarColor(name),
        color: '#fff',
        fontSize: Math.max(11, Math.round(size * 0.38)),
        lineHeight: 1,
        userSelect: 'none',
      }}
      role="img"
      aria-label={name}
    >
      {mark}
    </span>
  )
}

export default Avatar
