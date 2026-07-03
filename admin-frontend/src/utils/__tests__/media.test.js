import { describe, expect, it } from 'vitest'
import { videoCoverUrl, videoFileUrl } from '../media'

describe('media utils', () => {
  it('builds video media urls', () => {
    expect(videoCoverUrl('v1')).toBe('/api/v2/home/media/video-cover/v1.jpg')
    expect(videoFileUrl('v1')).toBe('/api/v2/home/media/video/v1.mp4')
    expect(videoCoverUrl()).toBe('')
    expect(videoFileUrl('')).toBe('')
  })
})
