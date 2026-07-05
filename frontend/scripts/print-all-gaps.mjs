import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const finalPath = join(process.cwd(), 'coverage', 'coverage-final.json')
if (!existsSync(finalPath)) {
  console.error('missing coverage-final.json')
  process.exit(1)
}

const coverage = JSON.parse(readFileSync(finalPath, 'utf8'))

function pct(hits, total) {
  if (!total) return 100
  return Math.round((hits / total) * 10000) / 100
}

function countHits(map, hitMap) {
  const keys = Object.keys(map || {})
  const hits = keys.filter((k) => (hitMap[k] ?? 0) > 0).length
  return { hits, total: keys.length }
}

for (const [file, data] of Object.entries(coverage)) {
  const normalized = file.replace(/^.*\/src\//, 'src/')
  const stmt = countHits(data.statementMap, data.s)
  const branch = countHits(data.branchMap, Object.fromEntries(
    Object.entries(data.b || {}).flatMap(([id, counts]) => counts.map((c, i) => [`${id}:${i}`, c])),
  ))
  const fn = countHits(data.fnMap, data.f)

  const linePct = pct(stmt.hits, stmt.total)
  const branchPct = pct(
    Object.values(data.b || {}).flat().filter((c) => c > 0).length,
    Object.values(data.b || {}).flat().length,
  )
  const fnPct = pct(fn.hits, fn.total)

  if (linePct >= 100 && branchPct >= 100 && fnPct >= 100) continue

  const uncovered = Object.entries(data.s || {})
    .filter(([, hits]) => hits === 0)
    .map(([id]) => data.statementMap?.[id]?.start?.line)
    .filter(Boolean)

  const branchLines = Object.entries(data.b || {})
    .filter(([, hits]) => hits.some((count) => count === 0))
    .map(([id]) => data.branchMap?.[id]?.line)
    .filter(Boolean)

  console.log(`${normalized}: L${linePct}% B${branchPct}% F${fnPct}%`)
  if (uncovered.length) console.log(`  lines: ${[...new Set(uncovered)].join(', ')}`)
  if (branchLines.length) console.log(`  branches: ${[...new Set(branchLines)].join(', ')}`)
}
