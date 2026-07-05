import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const finalPath = join(process.cwd(), 'coverage', 'coverage-final.json')
if (!existsSync(finalPath)) {
  console.error('missing coverage-final.json')
  process.exit(1)
}

const coverage = JSON.parse(readFileSync(finalPath, 'utf8'))
const target = process.argv[2] || ''

for (const [file, data] of Object.entries(coverage)) {
  const normalized = file.replace(/^.*\/src\//, 'src/')
  if (target && !normalized.includes(target)) continue

  const missedFns = Object.entries(data.fnMap || {}).filter(([id]) => !data.f[id])
  const missedBranches = Object.entries(data.branchMap || {}).filter(([, m], i) => {
    const hits = data.b[Object.keys(data.branchMap)[Object.entries(data.branchMap).findIndex(([k]) => k === Object.keys(data.branchMap)[i])]]
    return false
  })

  const missedBr = Object.entries(data.branchMap || {}).filter(([id]) => (data.b[id] || []).some((h) => h === 0))

  if (!missedFns.length && !missedBr.length) continue

  console.log(`\n${normalized}`)
  for (const [id, m] of missedFns) {
    const name = m.name || m.decl?.name || '(anonymous)'
    console.log(`  MISS fn ${name} @ line ${m.decl?.start?.line ?? m.loc?.start?.line}`)
  }
  for (const [id, m] of missedBr) {
    const miss = (data.b[id] || []).map((h, i) => (h === 0 ? i : null)).filter((x) => x !== null)
    console.log(`  MISS br line ${m.line} branches ${miss.join(',')}`)
  }
}
