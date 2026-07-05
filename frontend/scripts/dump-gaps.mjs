import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const finalPath = join(process.cwd(), 'coverage', 'coverage-final.json')
if (!existsSync(finalPath)) {
  console.error('missing coverage-final.json')
  process.exit(1)
}

const coverage = JSON.parse(readFileSync(finalPath, 'utf8'))
let totalLines = 0, coveredLines = 0, totalBranches = 0, coveredBranches = 0, totalFns = 0, coveredFns = 0

for (const [, data] of Object.entries(coverage)) {
  if (data.lines) {
    totalLines += data.lines.total
    coveredLines += data.lines.covered
  }
  if (data.branches) {
    totalBranches += data.branches.total
    coveredBranches += data.branches.covered
  }
  if (data.functions) {
    totalFns += data.functions.total
    coveredFns += data.functions.covered
  }
}

console.log('TOTAL lines', ((coveredLines / totalLines) * 100).toFixed(2) + '%')
console.log('TOTAL branches', ((coveredBranches / totalBranches) * 100).toFixed(2) + '%')
console.log('TOTAL functions', ((coveredFns / totalFns) * 100).toFixed(2) + '%')
console.log('---')

for (const [file, data] of Object.entries(coverage)) {
  const normalized = file.replace(/^.*\/src\//, 'src/')
  const linePct = data.lines?.pct ?? 100
  const branchPct = data.branches?.pct ?? 100
  const fnPct = data.functions?.pct ?? 100
  if (linePct >= 100 && branchPct >= 100 && fnPct >= 100) continue

  const uncovered = Object.entries(data.s || {})
    .filter(([, hits]) => hits === 0)
    .map(([line]) => Number(line))

  const branchMap = data.branchMap || {}
  const branches = Object.entries(data.b || {})
    .filter(([, hits]) => hits.some((count) => count === 0))
    .map(([id]) => branchMap[id]?.line)
    .filter(Boolean)

  console.log(`${normalized}: L${linePct}% B${branchPct}% F${fnPct}%`)
  if (uncovered.length) console.log(`  lines: ${uncovered.join(', ')}`)
  if (branches.length) console.log(`  branches: ${[...new Set(branches)].join(', ')}`)
}
