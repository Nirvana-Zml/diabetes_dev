import { describe, expect, it } from 'vitest'
import {
  encodeWavFromAudioBuffer,
  isWebmBlob,
  pickRecorderMimeType,
  voiceBlobFilename,
} from '@/composables/useVoiceInput'

describe('useVoiceInput helpers', () => {
  it('voiceBlobFilename maps mime types', () => {
    expect(voiceBlobFilename({ type: 'audio/mp4' })).toBe('voice.m4a')
    expect(voiceBlobFilename({ type: 'audio/mpeg' })).toBe('voice.mp3')
    expect(voiceBlobFilename({ type: 'audio/wav' })).toBe('voice.wav')
    expect(voiceBlobFilename({ type: 'audio/webm' })).toBe('voice.webm')
  })

  it('isWebmBlob detects webm and ogg', () => {
    expect(isWebmBlob({ type: 'audio/webm' })).toBe(true)
    expect(isWebmBlob({ type: 'audio/ogg' })).toBe(true)
    expect(isWebmBlob({ type: 'audio/wav' })).toBe(false)
  })

  it('encodeWavFromAudioBuffer produces wav blob', () => {
    const sampleRate = 8000
    const audioBuffer = {
      numberOfChannels: 1,
      sampleRate,
      length: 8,
      getChannelData: () => new Float32Array([0, 0.5, -0.5, 0.25, -0.25, 1, -1, 0]),
    }
    const wav = encodeWavFromAudioBuffer(audioBuffer)
    expect(wav.type).toBe('audio/wav')
    expect(wav.size).toBe(44 + 8 * 2)
  })

  it('pickRecorderMimeType returns string', () => {
    const mime = pickRecorderMimeType()
    expect(typeof mime).toBe('string')
  })
})
