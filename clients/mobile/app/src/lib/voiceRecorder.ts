import {
  AudioModule,
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  type AudioRecorder,
} from 'expo-audio';

export interface VoiceClip {
  uri: string;
  durationSec: number;
  mimeType: string;
  fileName: string;
}

export type VoiceRecorderErrorCode = 'permission-denied' | 'failed';

export class VoiceRecorderError extends Error {
  readonly code: VoiceRecorderErrorCode;

  constructor(code: VoiceRecorderErrorCode, message: string) {
    super(message);
    this.code = code;
  }
}

function mimeFromUri(uri: string): string {
  const ext = uri.split('?')[0].split('.').pop()?.toLowerCase();
  switch (ext) {
    case 'm4a':
    case 'mp4':
      return 'audio/mp4';
    case '3gp':
      return 'audio/3gpp';
    case 'webm':
      return 'audio/webm';
    case 'wav':
      return 'audio/wav';
    case 'aac':
      return 'audio/aac';
    default:
      return 'audio/mp4';
  }
}

function extensionOf(mimeType: string): string {
  switch (mimeType) {
    case 'audio/3gpp':
      return '3gp';
    case 'audio/webm':
      return 'webm';
    case 'audio/wav':
      return 'wav';
    case 'audio/aac':
      return 'aac';
    default:
      return 'm4a';
  }
}

/** Ghi âm một đoạn voice note. Mỗi phiên là một `VoiceRecorder` mới (start → stop hoặc cancel). */
export class VoiceRecorder {
  private recording: AudioRecorder | null = null;

  /** Xin quyền mic rồi bắt đầu ghi. Từ chối quyền thì ném `permission-denied` để UI báo nhẹ nhàng. */
  async start(): Promise<void> {
    let granted = false;
    try {
      granted = (await requestRecordingPermissionsAsync()).granted;
    } catch {
      granted = false;
    }
    if (!granted) {
      throw new VoiceRecorderError('permission-denied', 'Microphone permission is required to record.');
    }

    try {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      const recorder = new AudioModule.AudioRecorder(RecordingPresets.HIGH_QUALITY);
      await recorder.prepareToRecordAsync();
      recorder.record();
      this.recording = recorder;
    } catch {
      await this.resetAudioMode();
      throw new VoiceRecorderError('failed', 'Cannot start recording.');
    }
  }

  get isRecording(): boolean {
    return this.recording !== null;
  }

  /** Dừng và trả file ghi âm. */
  async stop(): Promise<VoiceClip> {
    const recording = this.recording;
    this.recording = null;
    if (!recording) {
      throw new VoiceRecorderError('failed', 'Not recording.');
    }
    try {
      await recording.stop();
      const uri = recording.uri;
      if (!uri) {
        throw new Error('missing uri');
      }
      const mimeType = mimeFromUri(uri);
      const status = recording.getStatus();
      const durationSec = Math.max(
        1,
        Math.round((status.durationMillis || recording.currentTime * 1000) / 1000),
      );
      return {
        uri,
        durationSec,
        mimeType,
        fileName: `voice-${Date.now()}.${extensionOf(mimeType)}`,
      };
    } catch {
      throw new VoiceRecorderError('failed', 'Cannot finish recording.');
    } finally {
      recording.release();
      await this.resetAudioMode();
    }
  }

  /** Bỏ bản ghi đang chạy mà không gửi. */
  async cancel(): Promise<void> {
    const recording = this.recording;
    this.recording = null;
    if (recording) {
      await recording.stop().catch(() => undefined);
      recording.release();
    }
    await this.resetAudioMode();
  }

  private async resetAudioMode(): Promise<void> {
    await setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
  }
}
