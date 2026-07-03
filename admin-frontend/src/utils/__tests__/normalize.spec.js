import { describe, expect, it } from 'vitest'
import {
  articleCategoryToInt,
  normalizeArticle,
  toCamelCase,
  toSnakeCase,
} from '../normalize'

describe('normalize utils', () => {
  it('converts nested keys between camelCase and snake_case', () => {
    expect(toSnakeCase({ userName: 'admin', records: [{ articleId: 'a1' }] })).toEqual({
      user_name: 'admin',
      records: [{ article_id: 'a1' }],
    })
    expect(toCamelCase({ user_name: 'admin', records: [{ article_id: 'a1' }] })).toEqual({
      userName: 'admin',
      records: [{ articleId: 'a1' }],
    })
    expect(toSnakeCase(null)).toBeNull()
    expect(toCamelCase('raw')).toBe('raw')
  })

  it('normalizes article category formats', () => {
    expect(articleCategoryToInt()).toBeUndefined()
    expect(articleCategoryToInt(3)).toBe(3)
    expect(articleCategoryToInt('diet')).toBe(2)
    expect(articleCategoryToInt('unknown')).toBe(1)
    expect(normalizeArticle({ articleId: 'a1', category: 2 })).toMatchObject({
      article_id: 'a1',
      category: 'diet',
    })
    expect(normalizeArticle({ articleId: 'a2', category: 99 })).toMatchObject({
      article_id: 'a2',
      category: '99',
    })
    expect(normalizeArticle(null)).toBeNull()
  })
})
