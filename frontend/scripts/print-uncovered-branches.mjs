import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const BUSINESS = [
  'src/api/',
  'src/stores/',
  'src/composables/',
  'src/test-interfaces/',
  'mergeAchievements.js',
  'checkin/utils.js',
]

const finalPath = join(process.cwd(), 'coverage', 'coverage-final.json')
if (!existsSync(finalPath)) {
  console.error('missing coverage-final.json')
  process.exit(1)
}

const coverage = JSON.parse(readFileSync(finalPath, 'utf8'))

for (const [file, data] of Object.entries(coverage)) {
  const normalized = file.replace(/^.*\/src\//, '/src/')
  if (!BUSINESS.some((prefix) => normalized.includes(prefix))) continue

  const branchMap = data.branchMap || {}
  const branches = data.b || {}
  const uncovered = Object.entries(branches)
    .filter(([, hits]) => hits.some((count) => count === 0))
    .map(([id]) => {
      const meta = branchMap[id]
      return meta ? `${meta.line}:${meta.type}` : id
    })

  if (!uncovered.length) continue
  const pct = data.b
    ? (Object.values(data.b).flat().filter((n) => n > 0).length
      / Object.values(data.b).flat().length * 100).toFixed(1)
    : '100'
  console.log(`${normalized} (${pct}% branches): ${uncovered.slice(0, 12).join(', ')}${uncovered.length > 12 ? ` +${uncovered.length - 12}` : ''}`)
}
