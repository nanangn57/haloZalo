
interface GroupSidebarProps {
  selectedFilter: 'all' | 'owned'
  onFilterChange: (filter: 'all' | 'owned') => void
}

function GroupSidebar({
  selectedFilter,
  onFilterChange,
}: GroupSidebarProps) {
  return (
    <aside
      className="border-end h-100 p-3"
      style={{ width: 240 }}
    >
      <h6 className="text-secondary mb-3">GROUPS</h6>

      <button
        type="button"
        className={`btn w-100 text-start mb-2 ${
          selectedFilter === 'all'
            ? 'btn-primary'
            : 'btn-light'
        }`}
        onClick={() => onFilterChange('all')}
      >
        <i className="bi bi-people me-2" />
        All groups
      </button>

      <button
        type="button"
        className={`btn w-100 text-start ${
          selectedFilter === 'owned'
            ? 'btn-primary'
            : 'btn-light'
        }`}
        onClick={() => onFilterChange('owned')}
      >
        <i className="bi bi-person-badge me-2" />
        Groups I own
      </button>
    </aside>
  )
}

export default GroupSidebar
