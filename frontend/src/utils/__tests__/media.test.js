import { describe, expect, it } from 'vitest'
import {
  bannerImageUrl,
  doctorAvatarUrl,
  resourceMediaUrl,
  videoCoverUrl,
  videoFileUrl,
} from '../media'

describe('media utils', () => {
  it('builds media proxy urls with default and explicit extensions', () => {
    expect(resourceMediaUrl('banner', 'b1')).toBe('/api/v2/home/media/banner/b1.jpg')
    expect(resourceMediaUrl('video', 'v1', 'mp4')).toBe('/api/v2/home/media/video/v1.mp4')
    expect(resourceMediaUrl('avatar', 'u1.png')).toBe('/api/v2/home/media/avatar/u1.png')
    expect(resourceMediaUrl('banner')).toBe('')
  })

  it('maps typed media helpers to their buckets', () => {
    expect(bannerImageUrl('b1')).toContain('/banner/b1.jpg')
    expect(videoCoverUrl('v1')).toContain('/video-cover/v1.jpg')
    expect(videoFileUrl('v1')).toContain('/video/v1.mp4')
    expect(doctorAvatarUrl('d1')).toContain('/avatar/d1.jpg')
  })
})
