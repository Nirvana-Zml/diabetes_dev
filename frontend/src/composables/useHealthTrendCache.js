const CACHE_KEY_PREFIX = 'health_trend_summary:'

export function readHealthTrendCache(userId) {
  if (!userId) return null
  try {
    const raw = localStorage.getItem(`${CACHE_KEY_PREFIX}${userId}`)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function writeHealthTrendCache(userId, trend, healthRecordedAt) {
  if (!userId || !trend?.summary) return
  try {
    localStorage.setItem(
      `${CACHE_KEY_PREFIX}${userId}`,
      JSON.stringify({
        summary: trend.summary,
        source: trend.source,
        healthRecordedAt: healthRecordedAt || null,
        updatedAt: Date.now(),
      }),
    )
  } catch {
    /* ignore quota errors */
  }
}

export function clearHealthTrendCache(userId) {
  if (!userId) return
  try {
    localStorage.removeItem(`${CACHE_KEY_PREFIX}${userId}`)
  } catch {
    /* ignore */
  }
}
