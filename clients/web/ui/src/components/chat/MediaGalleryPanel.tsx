import { useMemo, useState } from 'react'
import type { Message } from '@core/types/message'
import {
  buildMediaGallery,
  type GalleryItem,
  type GalleryMediaType,
} from '@core/mediaGallery'
import './MediaGalleryPanel.css'

interface MediaGalleryPanelProps {
  messages: Message[]
  getSenderName?: (userId: string) => string
  onClose: () => void
}

type Filter = GalleryMediaType | 'ALL'

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'IMAGE', label: 'Images' },
  { key: 'VIDEO', label: 'Videos' },
  { key: 'DOCUMENT', label: 'Files' },
  { key: 'AUDIO', label: 'Voice' },
]

function formatWhen(sentAt: number): string {
  return new Date(sentAt * 1000).toLocaleString()
}

function MediaGalleryPanel({
  messages,
  getSenderName,
  onClose,
}: MediaGalleryPanelProps) {
  const [filter, setFilter] = useState<Filter>('ALL')
  const [preview, setPreview] = useState<GalleryItem | null>(null)

  const items = useMemo(
    () => buildMediaGallery(messages, filter === 'ALL' ? undefined : filter),
    [messages, filter],
  )

  // Ảnh/video xem trước dạng lưới; tệp và voice dạng danh sách.
  const visual = items.filter(
    (item) => item.type === 'IMAGE' || item.type === 'VIDEO',
  )
  const others = items.filter(
    (item) => item.type === 'DOCUMENT' || item.type === 'AUDIO',
  )

  return (
    <div className="app-modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="app-modal media-gallery"
        role="dialog"
        aria-modal="true"
        aria-labelledby="media-gallery-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="app-modal-header">
          <h2 id="media-gallery-title">
            {preview ? 'Preview' : 'Media'}
          </h2>
          <button
            type="button"
            className="app-icon-button"
            aria-label="Close"
            onClick={preview ? () => setPreview(null) : onClose}
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>

        {preview ? (
          <div className="media-gallery-preview">
            {preview.type === 'IMAGE' ? (
              <img src={preview.url} alt="Shared media preview" />
            ) : (
              <video src={preview.url} controls autoPlay />
            )}
            <small className="text-muted">
              {getSenderName?.(preview.senderId) ?? 'User'} ·{' '}
              {formatWhen(preview.sentAt)}
            </small>
            <a
              href={preview.url}
              target="_blank"
              rel="noreferrer"
              className="btn btn-outline-secondary btn-sm"
            >
              Open in new tab
            </a>
          </div>
        ) : (
          <>
            <div className="media-gallery-filters" role="tablist">
              {FILTERS.map(({ key, label }) => (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={filter === key}
                  className={`btn btn-sm ${
                    filter === key ? 'btn-primary' : 'btn-outline-secondary'
                  }`}
                  onClick={() => setFilter(key)}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="media-gallery-body">
              {items.length === 0 ? (
                <p className="text-muted mb-0">
                  No media in the loaded messages.
                </p>
              ) : null}

              {visual.length > 0 ? (
                <div className="media-gallery-grid">
                  {visual.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      className="media-gallery-tile"
                      title={formatWhen(item.sentAt)}
                      aria-label={`Open ${item.type.toLowerCase()}`}
                      onClick={() => setPreview(item)}
                    >
                      {item.type === 'IMAGE' ? (
                        <img src={item.url} alt="" loading="lazy" />
                      ) : (
                        <i className="bi bi-play-circle" aria-hidden="true" />
                      )}
                    </button>
                  ))}
                </div>
              ) : null}

              {others.length > 0 ? (
                <div className="media-gallery-list">
                  {others.map((item) =>
                    item.type === 'AUDIO' ? (
                      <div key={item.id} className="media-gallery-list-row">
                        <i className="bi bi-mic" aria-hidden="true" />
                        <audio
                          src={item.url}
                          controls
                          preload="none"
                          style={{ flex: 1, minWidth: 0, height: 36 }}
                        />
                        <small className="text-muted">
                          {formatWhen(item.sentAt)}
                        </small>
                      </div>
                    ) : (
                      <a
                        key={item.id}
                        href={item.url}
                        target="_blank"
                        rel="noreferrer"
                        className="media-gallery-list-row"
                      >
                        <i
                          className="bi bi-file-earmark-text"
                          aria-hidden="true"
                        />
                        <span className="flex-grow-1">File</span>
                        <small className="text-muted">
                          {formatWhen(item.sentAt)}
                        </small>
                      </a>
                    ),
                  )}
                </div>
              ) : null}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default MediaGalleryPanel
