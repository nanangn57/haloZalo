interface GroupHeaderProps {
  onCreateGroup: () => void
}

function GroupHeader({ onCreateGroup }: GroupHeaderProps) {
  return (
    <header className="group-page-header">
      <div className="group-page-title">
        <span className="group-page-title-icon" aria-hidden="true">
          <i className="bi bi-people" />
        </span>
        <h1>Groups</h1>
      </div>

      <button
        type="button"
        className="app-button app-button-primary"
        onClick={onCreateGroup}
      >
        <i className="bi bi-plus-lg" aria-hidden="true" />
        Create group
      </button>
    </header>
  )
}

export default GroupHeader
