import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const target = process.argv[2] || 'useMessageCenter'
const finalPath = join(process.cwd(), 'coverage', 'coverage-final.json')
if (!existsSync(finalPath)) {
  console.error('missing coverage-final.json')
  process.exit(1)
}

const coverage = JSON.parse(readFileSync(finalPath, 'utf8'))
const file = Object.keys(coverage).find((k) => k.includes(target))
if (!file) {
  console.error('file not found:', target)
  process.exit(1)
}

const data = coverage[file]
for (const [id, hits] of Object.entries(data.b || {})) {
  if (!hits.some((h) => h === 0)) continue
  const m = data.branchMap[id]
  console.log(`${m.line}:${m.loc.start.column} hits=${JSON.stringify(hits)} type=${m.type}`)
}
