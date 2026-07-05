import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import {
  convertWebmToWav,
  encodeWavFromAudioBuffer,
  isWebmBlob,
  normalizeVoiceBlob,
  pickRecorderMimeType,
  useVoiceInput,
  voiceBlobFilename,
} from '@/composables/useVoiceInput'

describe('useVoiceInput helpers', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('voiceBlobFilename maps mime types', () => {
    expect(voiceBlobFilename({ type: 'audio/mp4' })).toBe('voice.m4a')
    expect(voiceBlobFilename({ type: 'audio/mpeg' })).toBe('voice.mp3')
    expect(voiceBlobFilename({ type: 'audio/wav' })).toBe('voice.wav')
    expect(voiceBlobFilename({ type: 'audio/amr' })).toBe('voice.amr')
    expect(voiceBlobFilename({ type: 'audio/webm' })).toBe('voice.webm')
    expect(voiceBlobFilename({ type: 'audio/unknown' })).toBe('voice.m4a')
    expect(voiceBlobFilename(null)).toBe('voice.m4a')
  })

  it('isWebmBlob detects webm and ogg', () => {
    expect(isWebmBlob({ type: 'audio/webm' })).toBe(true)
    expect(isWebmBlob({ type: 'audio/ogg' })).toBe(true)
    expect(isWebmBlob({ type: 'audio/wav' })).toBe(false)
    expect(isWebmBlob(null)).toBe(false)
  })

  it('encodeWavFromAudioBuffer produces wav blob', () => {
    const audioBuffer = {
      numberOfChannels: 1,
      sampleRate: 8000,
      length: 8,
      getChannelData: () => new Float32Array([0, 0.5, -0.5, 0.25, -0.25, 1, -1, 0]),
    }
    const wav = encodeWavFromAudioBuffer(audioBuffer)
    expect(wav.type).toBe('audio/wav')
    expect(wav.size).toBe(44 + 8 * 2)
  })

  it('pickRecorderMimeType returns empty when MediaRecorder is unavailable', () => {
    vi.stubGlobal('MediaRecorder', undefined)
    expect(pickRecorderMimeType()).toBe('')
  })

  it('pickRecorderMimeType prefers supported mime types', () => {
    vi.stubGlobal('MediaRecorder', {
      isTypeSupported: vi.fn((mime) => mime === 'audio/mp4'),
    })
    expect(pickRecorderMimeType()).toBe('audio/mp4')
  })

  it('pickRecorderMimeType falls back to webm', () => {
    vi.stubGlobal('MediaRecorder', {
      isTypeSupported: vi.fn((mime) => mime === 'audio/webm;codecs=opus'),
    })
    expect(pickRecorderMimeType()).toBe('audio/webm;codecs=opus')
  })

  it('normalizeVoiceBlob returns original blob for non-webm', async () => {
    const blob = new Blob(['wav'], { type: 'audio/wav' })
    await expect(normalizeVoiceBlob(blob)).resolves.toBe(blob)
    await expect(normalizeVoiceBlob(null)).resolves.toBe(null)
  })

  it('convertWebmToWav throws when AudioContext is unavailable', async () => {
    vi.stubGlobal('AudioContext', undefined)
    vi.stubGlobal('webkitAudioContext', undefined)
    const blob = new Blob(['webm'], { type: 'audio/webm' })
    await expect(convertWebmToWav(blob)).rejects.toThrow('当前浏览器无法转换录音格式')
  })

  it('convertWebmToWav decodes webm into wav', async () => {
    const close = vi.fn(async () => {})
    const decodeAudioData = vi.fn(async () => ({
      numberOfChannels: 1,
      sampleRate: 8000,
      length: 2,
      getChannelData: () => new Float32Array([0, 0.5]),
    }))
    vi.stubGlobal('AudioContext', vi.fn(() => ({ decodeAudioData, close })))
    const blob = new Blob(['webm'], { type: 'audio/webm' })
    const wav = await convertWebmToWav(blob)
    expect(wav.type).toBe('audio/wav')
    expect(close).toHaveBeenCalled()
  })

  it('normalizeVoiceBlob converts webm blobs', async () => {
    const close = vi.fn(async () => {})
    vi.stubGlobal('AudioContext', vi.fn(() => ({
      decodeAudioData: vi.fn(async () => ({
        numberOfChannels: 1,
        sampleRate: 8000,
        length: 1,
        getChannelData: () => new Float32Array([0.1]),
      })),
      close,
    })))
    const wav = await normalizeVoiceBlob(new Blob(['webm'], { type: 'audio/webm' }))
    expect(wav.type).toBe('audio/wav')
  })
})

describe('useVoiceInput composable', () => {
  let mediaRecorderInstance

  beforeEach(() => {
    vi.useFakeTimers()
    mediaRecorderInstance = {
      mimeType: 'audio/webm',
      state: 'recording',
      start: vi.fn(),
      stop: vi.fn(function stop() {
        this.state = 'inactive'
        this.onstop?.()
      }),
      ondataavailable: null,
      onstop: null,
      onerror: null,
    }
    vi.stubGlobal('MediaRecorder', vi.fn(() => mediaRecorderInstance))
    MediaRecorder.isTypeSupported = vi.fn(() => true)
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: vi.fn(async () => ({
          getTracks: () => [{ stop: vi.fn() }],
        })),
      },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('starts and stops recording', async () => {
    const { recording, start, stop } = useVoiceInput(1000)
    await start()
    expect(recording.value).toBe(true)
    mediaRecorderInstance.ondataavailable({ data: new Blob(['a'], { type: 'audio/webm' }) })
    const blob = await stop()
    expect(blob?.type).toContain('webm')
    expect(recording.value).toBe(false)
  })

  it('ignores duplicate start and empty stop', async () => {
    const { recording, start, stop } = useVoiceInput()
    await start()
    await start()
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledTimes(1)
    recording.value = false
    await expect(stop()).resolves.toBeNull()
  })

  it('throws when browser does not support getUserMedia', async () => {
    navigator.mediaDevices = undefined
    const { start } = useVoiceInput()
    await expect(start()).rejects.toThrow('当前浏览器不支持录音')
  })

  it('throws when no supported mime type exists', async () => {
    MediaRecorder.isTypeSupported = vi.fn(() => false)
    const { start } = useVoiceInput()
    await expect(start()).rejects.toThrow('当前浏览器不支持麦克风录音')
  })

  it('auto stops after max duration', async () => {
    const { recording, start } = useVoiceInput(500)
    await start()
    mediaRecorderInstance.ondataavailable({ data: new Blob(['a']) })
    vi.advanceTimersByTime(500)
    await nextTick()
    expect(recording.value).toBe(false)
  })

  it('rejects when recorder errors', async () => {
    const { start, stop } = useVoiceInput()
    await start()
    mediaRecorderInstance.stop = vi.fn(function stopWithError() {
      this.onerror?.()
    })
    await expect(stop()).rejects.toThrow('录音失败')
  })

  it('resolves null when recorder stops without chunks', async () => {
    const { start, stop } = useVoiceInput()
    await start()
    await expect(stop()).resolves.toBeNull()
  })

  it('uses audio/webm fallback when mimeType and pickRecorderMimeType are empty', async () => {
    const { start, stop } = useVoiceInput()
    await start()
    mediaRecorderInstance.mimeType = ''
    MediaRecorder.isTypeSupported = vi.fn(() => false)
    mediaRecorderInstance.ondataavailable({ data: new Blob(['a']) })
    const blob = await stop()
    expect(blob?.type).toBe('audio/webm')
  })

  it('uses pickRecorderMimeType when mediaRecorder.mimeType is empty', async () => {
    const { start, stop } = useVoiceInput()
    await start()
    mediaRecorderInstance.mimeType = ''
    mediaRecorderInstance.ondataavailable({ data: new Blob(['a']) })
    const blob = await stop()
    expect(blob?.type).toBeTruthy()
  })

  it('cleans up on unmount', async () => {
    const trackStop = vi.fn()
    navigator.mediaDevices.getUserMedia.mockResolvedValueOnce({
      getTracks: () => [{ stop: trackStop }],
    })
    const { defineComponent, onMounted } = await import('vue')
    const { mount } = await import('@vue/test-utils')
    const Comp = defineComponent({
      setup() {
        const voice = useVoiceInput(1000)
        onMounted(() => voice.start())
        return voice
      },
      template: '<div />',
    })
    const wrapper = mount(Comp)
    await nextTick()
    mediaRecorderInstance.ondataavailable({ data: new Blob(['a']) })
    wrapper.unmount()
    vi.advanceTimersByTime(1000)
    expect(trackStop).toHaveBeenCalled()
  })
})
