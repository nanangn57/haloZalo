import type { User } from '../../types/user'

interface UserInfoPanelProps {
  user: User
  onClose: () => void
}

function UserInfoPanel({ user, onClose }: UserInfoPanelProps) {
  return (
    <aside className="d-flex flex-column h-100">
      {/* Header */}
      <div className="d-flex align-items-center justify-content-between p-3 border-bottom">
        <h6 className="mb-0 fw-bold">Account Information</h6>

        <button
          type="button"
          className="btn btn-sm text-light border-0"
          onClick={onClose}
          aria-label="Đóng thông tin tài khoản"
        >
          <i className="bi bi-x-lg fs-4" />
        </button>
      </div>

      {/* Ảnh bìa */}
      <img
        src="https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800"
        alt="Ảnh bìa"
        className="user-cover"
      />

      {/* Avatar và tên */}
      <div className="px-3 pb-3">
        <div className="d-flex align-items-center gap-3 user-profile">
          <img
            src={user.avatar}
            alt={user.name}
            className="rounded-circle me-2"
          />

          <div className="d-flex align-items-center gap-2 min-w-0">
            <h5 className="mb-0 fw-bold text-truncate">
              {user.name}
            </h5>

            <button
              type="button"
              className="btn btn-sm text-light border-0"
              title="Chỉnh sửa tên"
            >
              <i className="bi bi-pencil" />
            </button>
          </div>
        </div>

        {/* Nút thao tác */}
        <div className="row mt-1">
          <div className="col-6">
            <button
              type="button"
              className="btn btn-primary w-100 fw-semibold"
            >
              Text
            </button>
          </div>
        </div>
      </div>

      {/* Thông tin cá nhân */}
      <section className=" p-3 border-top">
        <h6 className="fw-bold mb-4">Personal Information</h6>

        <div className="d-flex flex-column gap-3">
          <div className="row">
            <div className="col-6 text-secondary">
              Gender
            </div>
            <div className="col-6">Male</div>
          </div>

          <div className="row">
            <div className="col-6 text-secondary">
              Birthday
            </div>
            <div className="col-6">••/••/••••</div>
          </div>

          <div className="row">
            <div className="col-6 text-secondary">
              Phone number
            </div>
            <div className="col-6">••••••••••</div>
          </div>
        </div>
      </section>
    </aside>
  )
}

export default UserInfoPanel