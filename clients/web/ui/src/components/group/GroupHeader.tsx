
interface GroupHeaderProps {
  onCreateGroup: () => void
}

function GroupHeader({ onCreateGroup }: GroupHeaderProps) {
  return (
    <header className="d-flex align-items-center justify-content-between border-bottom px-4 py-3">
      <div className="d-flex align-items-center gap-3">
        <i className="bi bi-people fs-4" />
        <h5 className="mb-0">Groups</h5>
      </div>

      <button
        type="button"
        className="btn btn-primary"
        onClick={onCreateGroup}
      >
        <i className="bi bi-plus-lg me-2" />
        Create group
      </button>
    </header>
  )
}

export default GroupHeader
