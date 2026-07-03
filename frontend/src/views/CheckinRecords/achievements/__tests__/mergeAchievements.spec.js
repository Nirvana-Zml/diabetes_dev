import { describe, it, expect, vi } from 'vitest'
import { buildAchievementWall, filterAchievements } from '../mergeAchievements'

describe('buildAchievementWall', () => {
  it('merges api unlocks and stats rules', () => {
    const wall = buildAchievementWall(
      [{ name: '首次打卡', unlocked: true }],
      { total_checkins: 5, streak_days: 3 },
    )

    expect(wall.total).toBe(12)
    expect(wall.achievements.find((a) => a.id === 'first_checkin')?.unlocked).toBe(true)
    expect(wall.achievements.find((a) => a.id === 'streak_7')?.unlock_label).toMatch(/还差/)
  })

  it('computes streak-based unlock from stats', () => {
    const wall = buildAchievementWall([], { streak_days: 12, total_checkins: 20 })

    expect(wall.achievements.find((a) => a.id === 'streak_7')?.unlocked).toBe(true)
    expect(wall.unlockedCount).toBeGreaterThanOrEqual(2)
  })

  it('uses unlock dates and recent unlock metadata', () => {
    const wall = buildAchievementWall([
      {
        name: '运动达人',
        unlocked: true,
        unlocked_at: '2024-01-18',
      },
      {
        name: '坚持不懈',
        unlocked: true,
        unlocked_at: '2024-01-25',
      },
    ], { exercise_count: 30, streak_days: 8 })

    const exercise = wall.achievements.find((a) => a.id === 'exercise_master')
    expect(exercise?.unlock_label).toBe('2024.01.18 解锁')
    expect(wall.recentUnlock?.name).toBe('坚持不懈')
  })

  it('handles api alias mapping and unlocked without date', () => {
    const wall = buildAchievementWall([
      { name: '连续打卡达人', unlocked: true, unlocked_at: '2024-01-12' },
      { name: '打卡王者', unlocked: true },
    ], { streak_days: 30 })

    expect(wall.achievements.find((a) => a.id === 'streak_7')?.unlocked).toBe(true)
    const unlockedNoDate = wall.achievements.find((a) => a.unlocked && !a.unlocked_at)
    expect(unlockedNoDate?.unlock_label).toBe('已解锁')
  })

  it('formats progress hints for locked achievements', () => {
    const wall = buildAchievementWall([], {
      streak_days: 12,
      medication_streak: 18,
      dinner_count: 13,
      perfect_days: 0,
    })

    expect(wall.achievements.find((a) => a.id === 'century')?.unlock_label).toBe('还差 88 天')
    expect(wall.achievements.find((a) => a.id === 'night_diner')?.unlock_label).toBe('还差 7 次')
    expect(wall.achievements.find((a) => a.id === 'perfect_day')?.unlock_label).toBe('待完成')
  })

  it('filters achievement list', () => {
    const list = [
      { id: 'a', unlocked: true },
      { id: 'b', unlocked: false },
    ]
    expect(filterAchievements(list, 'unlocked')).toHaveLength(1)
    expect(filterAchievements(list, 'locked')).toHaveLength(1)
    expect(filterAchievements(list, 'all')).toHaveLength(2)
  })

  it('covers camelCase stats, unlock date ordering and progress edge cases', () => {
    const wall = buildAchievementWall([
      {
        name: '连续打卡达人',
        unlocked: true,
        unlocked_at: '2024-01-10',
      },
      {
        name: '连续打卡达人',
        unlocked: true,
        unlocked_at: '2024-01-20',
      },
      { name: '首次打卡', unlocked: true, unlockedAt: '2024-01-05' },
    ], {
      totalCheckins: 1,
      streakDays: 6,
      foodCount: 99,
    }, {
      defaultUnlockDate: '2024-01-01',
    })

    expect(wall.achievements.find((a) => a.id === 'streak_7')?.unlock_label).toBe('2024.01.20 解锁')
    expect(wall.achievements.find((a) => a.id === 'food_expert')?.unlock_label).toBe('还差 1 次')
    expect(wall.achievements.find((a) => a.id === 'first_checkin')?.unlock_label).toBe('2024.01.05 解锁')
    expect(wall.recentUnlock?.relative).toMatch(/前|刚刚/)
  })

  it('uses default unlock date and recent unlock without timestamp', () => {
    const wall = buildAchievementWall([], {
      totalCheckins: 10,
      streakDays: 8,
    }, {
      defaultUnlockDate: '2024-02-01',
    })

    const ruleUnlocked = wall.achievements.find((a) => a.id === 'streak_7')
    expect(ruleUnlocked?.unlocked).toBe(true)
    expect(ruleUnlocked?.unlock_label).toBe('2024.02.01 解锁')

    const noDateWall = buildAchievementWall([
      { name: '打卡王者', unlocked: true },
    ], { streakDays: 100 })
    expect(noDateWall.recentUnlock?.relative).toBe('刚刚')
  })

  it('covers perfect day progress hint and empty recent unlock', () => {
    const emptyWall = buildAchievementWall([], {})
    expect(emptyWall.recentUnlock).toBeNull()
    expect(emptyWall.progressPercent).toBe(0)

    const perfectWall = buildAchievementWall([], { perfect_days: 0 })
    expect(perfectWall.achievements.find((a) => a.id === 'perfect_day')?.unlock_label).toBe('待完成')

    const camelWall = buildAchievementWall([], {
      totalCheckins: 1,
      streakDays: 1,
      foodCount: 1,
      exerciseCount: 1,
      medicationCount: 1,
      glucoseCount: 1,
      medicationStreak: 1,
      glucoseStreak: 1,
      earlyBirdStreak: 1,
      calorieStreak: 1,
      dinnerCount: 1,
      perfectDays: 0,
    })
    expect(camelWall.achievements.find((a) => a.id === 'first_checkin')?.unlocked).toBe(true)
  })

  it('covers unlock date ordering and progress remaining zero hint', () => {
    const wall = buildAchievementWall([
      { name: '连续打卡达人', unlocked: true, unlocked_at: '2024-01-01' },
      { name: '连续打卡达人', unlocked: true, unlocked_at: '2024-02-01' },
    ], { streak_days: 6 })

    expect(wall.achievements.find((a) => a.id === 'streak_7')?.unlock_label).toMatch(/2024.02.01|还差/)
  })

  it('shows imminent unlock hint when progress remaining is zero', async () => {
    vi.doMock('../constants', async (importActual) => {
      const actual = await importActual()
      return {
        ...actual,
        ACHIEVEMENT_CATALOG: [{
          id: 'edge_imminent',
          name: '边界成就',
          desc: '测试',
          emoji: 'E',
          theme: 'slate',
          evaluate: () => false,
          progress: () => ({ unit: '次', remaining: 0 }),
        }],
      }
    })
    vi.resetModules()
    const { buildAchievementWall } = await import('../mergeAchievements')
    const wall = buildAchievementWall([], {})
    expect(wall.achievements[0].unlock_label).toBe('即将解锁')
    vi.doUnmock('../constants')
    vi.resetModules()
    await import('../mergeAchievements')
  })

  it('uses override unlock dates when provided', () => {
    const overrideWall = buildAchievementWall([], { streak_days: 7 }, {
      unlockDates: { streak_7: '2024-03-15' },
    })
    expect(overrideWall.achievements.find((a) => a.id === 'streak_7')?.unlock_label).toBe('2024.03.15 解锁')
  })

  it('covers progress without unit and catalog without evaluate', async () => {
    vi.doMock('../constants', async (importActual) => {
      const actual = await importActual()
      return {
        ...actual,
        ACHIEVEMENT_CATALOG: [
          {
            id: 'no_eval',
            name: '无规则',
            desc: 'd',
            emoji: 'N',
            theme: 'slate',
          },
          {
            id: 'no_unit',
            name: '无单位',
            desc: 'd',
            emoji: 'U',
            theme: 'slate',
            evaluate: () => false,
            progress: () => ({ unit: '', remaining: 3 }),
          },
        ],
      }
    })
    vi.resetModules()
    const { buildAchievementWall } = await import('../mergeAchievements')
    const wall = buildAchievementWall([], {})
    expect(wall.achievements.find((a) => a.id === 'no_eval')?.unlocked).toBe(false)
    expect(wall.achievements.find((a) => a.id === 'no_unit')?.unlock_label).toBe('待完成')
    expect(wall.progressPercent).toBe(0)
    vi.doUnmock('../constants')
    vi.resetModules()
    await import('../mergeAchievements')
  })

  it('skips stale unlock dates and handles achievements without progress fn', () => {
    const wall = buildAchievementWall([
      { name: '连续打卡达人', unlocked: true, unlocked_at: '2024-02-01' },
      { name: '连续打卡达人', unlocked: true, unlocked_at: '2024-01-01' },
      { name: '首次打卡', unlocked: true, unlocked_at: '' },
    ], { total_checkins: 0 })

    expect(wall.achievements.find((a) => a.id === 'streak_7')?.unlock_label).toMatch(/2024\.\d{2}\.\d{2} 解锁/)
    expect(wall.achievements.find((a) => a.id === 'first_checkin')?.unlock_label).toBe('已解锁')
    expect(wall.progressPercent).toBeGreaterThan(0)
  })

  it('covers formatUnlockDate empty value and zero total progress', async () => {
    const { achievementWallTestUtils, buildAchievementWall } = await import('../mergeAchievements')
    expect(achievementWallTestUtils.formatUnlockDate('')).toBe('')
    expect(achievementWallTestUtils.formatUnlockDate(null)).toBe('')

    vi.doMock('../constants', async (importActual) => {
      const actual = await importActual()
      return { ...actual, ACHIEVEMENT_CATALOG: [] }
    })
    vi.resetModules()
    const mod = await import('../mergeAchievements')
    expect(mod.buildAchievementWall([], {}).progressPercent).toBe(0)
    vi.doUnmock('../constants')
    vi.resetModules()
    await import('../mergeAchievements')
  })
})
