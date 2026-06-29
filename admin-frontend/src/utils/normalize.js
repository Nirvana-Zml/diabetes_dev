/** 将 camelCase 键转为 snake_case（浅层对象/数组） */
export function toSnakeCase(data) {
  if (data == null || typeof data !== 'object') return data
  if (Array.isArray(data)) return data.map(toSnakeCase)
  return Object.fromEntries(
    Object.entries(data).map(([k, v]) => [k.replace(/([A-Z])/g, '_$1').toLowerCase(), toSnakeCase(v)]),
  )
}

/** 将 snake_case 键转为 camelCase（浅层，用于请求体） */
export function toCamelCase(data) {
  if (data == null || typeof data !== 'object') return data
  if (Array.isArray(data)) return data.map(toCamelCase)
  return Object.fromEntries(
    Object.entries(data).map(([k, v]) => [
      k.replace(/_([a-z])/g, (_, c) => c.toUpperCase()),
      toCamelCase(v),
    ]),
  )
}

const ARTICLE_CATEGORY_TO_INT = {
  diabetes_basics: 1,
  diet: 2,
  exercise: 3,
  medication: 4,
  complications: 5,
}

const ARTICLE_CATEGORY_TO_STR = Object.fromEntries(
  Object.entries(ARTICLE_CATEGORY_TO_INT).map(([k, v]) => [String(v), k]),
)

export function articleCategoryToInt(category) {
  if (!category) return undefined
  if (typeof category === 'number') return category
  return ARTICLE_CATEGORY_TO_INT[category] ?? 1
}

export function normalizeArticle(item) {
  if (!item) return item
  const a = toSnakeCase(item)
  if (typeof a.category === 'number') {
    a.category = ARTICLE_CATEGORY_TO_STR[a.category] || String(a.category)
  }
  return a
}
