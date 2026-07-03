import { ref, onUnmounted } from 'vue'

/** Dify 音频开始节点支持：MP3, M4A, WAV, AMR, MPGA（不支持 WebM） */
const PREFERRED_MIME_TYPES = [
  'audio/mp4',
  'audio/mpeg',
  'audio/wav',
  'audio/x-wav',
]

/** Chrome 等浏览器通常仅支持 WebM，录完后由 normalizeVoiceBlob 转为 WAV */
const FALLBACK_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
]

export function pickRecorderMimeType() {
  if (typeof MediaRecorder === 'undefined') return ''
  for (const mime of PREFERRED_MIME_TYPES) {
    if (MediaRecorder.isTypeSupported(mime)) return mime
  }
  for (const mime of FALLBACK_MIME_TYPES) {
    if (MediaRecorder.isTypeSupported(mime)) return mime
  }
  return ''
}

export function isWebmBlob(blob) {
  const type = (blob?.type || '').toLowerCase()
  if (type.includes('webm') || type.includes('ogg')) return true
  return false
}

/** 将 AudioBuffer 编码为 16-bit PCM WAV */
export function encodeWavFromAudioBuffer(audioBuffer) {
  const numChannels = audioBuffer.numberOfChannels
  const sampleRate = audioBuffer.sampleRate
  const samples = audioBuffer.length
  const bytesPerSample = 2
  const blockAlign = numChannels * bytesPerSample
  const dataSize = samples * blockAlign
  const buffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buffer)

  const writeString = (offset, value) => {
    for (let i = 0; i < value.length; i += 1) {
      view.setUint8(offset + i, value.charCodeAt(i))
    }
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + dataSize, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, numChannels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * blockAlign, true)
  view.setUint16(32, blockAlign, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, dataSize, true)

  const channels = []
  for (let ch = 0; ch < numChannels; ch += 1) {
    channels.push(audioBuffer.getChannelData(ch))
  }

  let offset = 44
  for (let i = 0; i < samples; i += 1) {
    for (let ch = 0; ch < numChannels; ch += 1) {
      const sample = Math.max(-1, Math.min(1, channels[ch][i]))
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
      offset += 2
    }
  }

  return new Blob([buffer], { type: 'audio/wav' })
}

/** 将 Chrome WebM 录音转为 Dify 可接受的 WAV */
export async function convertWebmToWav(blob) {
  const arrayBuffer = await blob.arrayBuffer()
  const AudioCtx = window.AudioContext || window.webkitAudioContext
  if (!AudioCtx) {
    throw new Error('当前浏览器无法转换录音格式，请尝试 Safari 或 Edge')
  }
  const audioContext = new AudioCtx()
  try {
    const audioBuffer = await audioContext.decodeAudioData(arrayBuffer.slice(0))
    return encodeWavFromAudioBuffer(audioBuffer)
  } finally {
    await audioContext.close()
  }
}

/** 上传前规范化：WebM/Ogg → WAV，其余格式原样返回 */
export async function normalizeVoiceBlob(blob) {
  if (!blob || !isWebmBlob(blob)) return blob
  return convertWebmToWav(blob)
}

export function voiceBlobFilename(blob) {
  const type = blob?.type || ''
  if (type.includes('mpeg') || type.includes('mp3')) return 'voice.mp3'
  if (type.includes('wav')) return 'voice.wav'
  if (type.includes('amr')) return 'voice.amr'
  if (type.includes('mp4') || type.includes('m4a')) return 'voice.m4a'
  if (type.includes('webm')) return 'voice.webm'
  return 'voice.m4a'
}

/**
 * 浏览器麦克风录音（用于 AI 科普助手语音输入）。
 * Chrome 等浏览器会录 WebM，上传前由 voiceToText 转为 WAV。
 */
export function useVoiceInput(maxDurationMs = 60_000) {
  const recording = ref(false)
  let mediaRecorder = null
  let mediaStream = null
  let chunks = []
  let stopTimer = null

  function cleanupStream() {
    if (mediaStream) {
      mediaStream.getTracks().forEach((track) => track.stop())
      mediaStream = null
    }
  }

  async function start() {
    if (recording.value) return
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('当前浏览器不支持录音')
    }
    const mimeType = pickRecorderMimeType()
    if (!mimeType) {
      throw new Error('当前浏览器不支持麦克风录音')
    }
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaStream = stream
    mediaRecorder = new MediaRecorder(stream, { mimeType })
    chunks = []
    mediaRecorder.ondataavailable = (event) => {
      if (event.data?.size) chunks.push(event.data)
    }
    mediaRecorder.start()
    recording.value = true
    stopTimer = setTimeout(() => {
      if (recording.value) stop()
    }, maxDurationMs)
  }

  function stop() {
    return new Promise((resolve, reject) => {
      if (!mediaRecorder || !recording.value) {
        resolve(null)
        return
      }
      clearTimeout(stopTimer)
      mediaRecorder.onstop = () => {
        recording.value = false
        const type = mediaRecorder.mimeType || pickRecorderMimeType() || 'audio/webm'
        cleanupStream()
        resolve(chunks.length ? new Blob(chunks, { type }) : null)
      }
      mediaRecorder.onerror = () => {
        recording.value = false
        cleanupStream()
        reject(new Error('录音失败'))
      }
      mediaRecorder.stop()
    })
  }

  onUnmounted(() => {
    clearTimeout(stopTimer)
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop()
    }
    cleanupStream()
  })

  return { recording, start, stop }
}
