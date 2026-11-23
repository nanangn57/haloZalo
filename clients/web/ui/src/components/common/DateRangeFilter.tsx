import { useEffect, useState } from 'react'
import { rangePreset, type DateRange } from '@core/statsService'

interface DateRangeFilterProps {
  value: DateRange
  onApply: (range: DateRange) => void
  disabled?: boolean
}

type Preset = 'today' | '7d' | '30d' | 'custom' | 'all'

function matchPreset(value: DateRange): Preset {
  if (!value.from && !value.to) return 'all'
  const today = rangePreset('today')
  const week = rangePreset('7d')
  const month = rangePreset('30d')
  if (value.from === today.from && value.to === today.to) return 'today'
  if (value.from === week.from && value.to === week.to) return '7d'
  if (value.from === month.from && value.to === month.to) return '30d'
  return 'custom'
}

function DateRangeFilter({ value, onApply, disabled }: DateRangeFilterProps) {
  const [from, setFrom] = useState(value.from ?? '')
  const [to, setTo] = useState(value.to ?? '')
  const [preset, setPreset] = useState<Preset>(() => matchPreset(value))
  const invalid = Boolean(from && to && from > to)

  useEffect(() => {
    setFrom(value.from ?? '')
    setTo(value.to ?? '')
    setPreset(matchPreset(value))
  }, [value.from, value.to])

  function applyPreset(next: Preset) {
    setPreset(next)
    if (next === 'all') {
      setFrom('')
      setTo('')
      onApply({})
      return
    }
    if (next === 'custom') {
      return
    }
    const range = rangePreset(next)
    setFrom(range.from ?? '')
    setTo(range.to ?? '')
    onApply(range)
  }

  return (
    <div className="activity-filter">
      <div className="activity-preset-row" role="group" aria-label="Date range presets">
        {(
          [
            ['today', 'Today'],
            ['7d', '7 days'],
            ['30d', '30 days'],
            ['all', 'All time'],
            ['custom', 'Custom'],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            className={`activity-preset${preset === key ? ' is-active' : ''}`}
            disabled={disabled}
            onClick={() => applyPreset(key)}
          >
            {label}
          </button>
        ))}
      </div>

      {preset === 'custom' ? (
        <form
          className="d-flex flex-wrap align-items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (!invalid) onApply({ from: from || undefined, to: to || undefined })
          }}
        >
          <div>
            <label htmlFor="range-from" className="app-form-label">
              From
            </label>
            <input
              id="range-from"
              type="date"
              className="app-input"
              value={from}
              max={to || undefined}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="range-to" className="app-form-label">
              To
            </label>
            <input
              id="range-to"
              type="date"
              className="app-input"
              value={to}
              min={from || undefined}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
          <button
            type="submit"
            className="app-button app-button-primary"
            disabled={disabled || invalid}
          >
            Apply
          </button>
          {invalid ? (
            <span className="text-danger small">“From” must not be after “To”.</span>
          ) : null}
        </form>
      ) : null}
    </div>
  )
}

export default DateRangeFilter
