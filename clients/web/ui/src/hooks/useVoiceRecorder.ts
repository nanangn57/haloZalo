import { useCallback, useEffect, useRef, useState } from 'react'

export interface VoiceClip {
  blob: Blob
  durationSec: number
  mimeType: string
  fileName: string
}

export type VoiceRecorderStatus = 'idle' | 'recording'

/** Nhấn nhanh hơn mức này coi như bấm nhầm, không gửi. */
const MIN_RECORD_MS = 600

const PREFERRED_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/mp4',
  'audio/ogg;codecs=opus',
]

function pickMimeType(): string | undefined {
  if (typeof MediaRecorder === 'undefined') return undefined
  return PREFERRED_TYPES.find((type) => MediaRecorder.isTypeSupported(type))
}

function extensionOf(mimeType: string): string {
  if (mimeType.includes('mp4')) return 'm4a'
  if (mimeType.includes('ogg')) return 'ogg'
  return 'webm'
}

export function isVoiceRecordingSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    Boolean(navigator.mediaDevices?.getUserMedia) &&
    typeof MediaRecorder !== 'undefined'
  )
}

/**
 * Ghi âm bằng MediaRecorder cho kiểu "giữ để ghi".
 * `stop()` chờ `start()` xong nên nhả nút quá nhanh (trước khi trình duyệt cấp mic) vẫn an toàn.
 */
export function useVoiceRecorder() {
  const [status, setStatus] = useState<VoiceRecorderStatus>('idle')
  const [error, setError] = useState<string | null>(null)

  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const startedAtRef = useRef(0)
  const startPromiseRef = useRef<Promise<void> | null>(null)

  const releaseStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
  }, [])

  const start = useCallback(async () => {
    if (recorderRef.current || startPromiseRef.current) return
    setError(null)

    if (!isVoiceRecordingSupported()) {
      setError('Voice recording is not supported in this browser.')
      return
    }

    const run = (async () => {
      try {
        // Mic only works on https or localhost — LAN IP (http://192.168.x.x) is blocked.
        if (
          typeof window !== 'undefined' &&
          !window.isSecureContext &&
          window.location.hostname !== 'localhost' &&
          window.location.hostname !== '127.0.0.1'
        ) {
          setError(
            'Microphone needs https or localhost. Open http://localhost:5173 instead of a LAN IP.',
          )
          return
        }
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        const mimeType = pickMimeType()
        const recorder = new MediaRecorder(
          stream,
          mimeType ? { mimeType } : undefined,
        )
        chunksRef.current = []
        recorder.ondataavailable = (event) => {
          if (event.data.size > 0) chunksRef.current.push(event.data)
        }
        streamRef.current = stream
        recorderRef.current = recorder
        startedAtRef.current = Date.now()
        // timeslice so short holds still produce chunks on some browsers
        recorder.start(200)
        setStatus('recording')
      } catch (err) {
        const denied =
          err instanceof DOMException &&
          (err.name === 'NotAllowedError' || err.name === 'SecurityError')
        setError(
          denied
            ? 'Microphone permission denied. Allow it in the browser to send voice messages.'
            : err instanceof Error
              ? err.message
              : 'Cannot access the microphone.',
        )
      }
    })()

    startPromiseRef.current = run
    await run
    startPromiseRef.current = null
  }, [])

  /** Dừng ghi và trả clip, hoặc `null` nếu chưa ghi được gì. */
  const stop = useCallback(async (): Promise<VoiceClip | null> => {
    if (startPromiseRef.current) {
      await startPromiseRef.current
    }
    const recorder = recorderRef.current
    if (!recorder) return null
    recorderRef.current = null

    const heldMs = Date.now() - startedAtRef.current
    const durationSec = Math.max(1, Math.round(heldMs / 1000))

    const finished = new Promise<void>((resolve) => {
      recorder.onstop = () => resolve()
    })
    if (recorder.state !== 'inactive') recorder.stop()
    await finished

    releaseStream()
    setStatus('idle')

    const recordedType = recorder.mimeType || pickMimeType() || 'audio/webm'
    // Server chỉ cần `audio/*`; bỏ tham số codecs khỏi MIME gửi đi.
    const mimeType = recordedType.split(';')[0]
    const blob = new Blob(chunksRef.current, { type: mimeType })
    chunksRef.current = []
    if (blob.size === 0) return null
    if (heldMs < MIN_RECORD_MS) {
      setError('Hold the mic button a little longer to record.')
      return null
    }

    return {
      blob,
      durationSec,
      mimeType,
      fileName: `voice-${Date.now()}.${extensionOf(mimeType)}`,
    }
  }, [releaseStream])

  const cancel = useCallback(async () => {
    await stop()
  }, [stop])

  // Rời trang khi đang ghi thì nhả mic.
  useEffect(() => {
    return () => {
      const recorder = recorderRef.current
      recorderRef.current = null
      if (recorder && recorder.state !== 'inactive') {
        recorder.onstop = null
        recorder.stop()
      }
      releaseStream()
    }
  }, [releaseStream])

  return { status, error, clearError: () => setError(null), start, stop, cancel }
}
