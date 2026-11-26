import { useState } from 'react'
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
  const url = typeof src === 'string' ? src.trim() : ''
  // Track which URL failed — when `url` changes, failedUrl !== url so we retry without an effect.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const imageFailed = Boolean(url) && failedUrl === url

  if (url && !imageFailed) {
    return (
      <img
        src={url}
        alt=""
        className={`rounded-circle flex-shrink-0 ${className}`}
        width={size}
        height={size}
        style={{
          width: size,
          height: size,
          objectFit: 'cover',
          objectPosition: 'center',
          boxSizing: 'border-box',
          overflow: 'hidden',
        }}
        onError={() => setFailedUrl(url)}
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
        fontSize: Math.max(11, Math.round(size * 0.36)),
        lineHeight: 1,
        letterSpacing: '-0.02em',
        boxSizing: 'border-box',
        overflow: 'hidden',
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
