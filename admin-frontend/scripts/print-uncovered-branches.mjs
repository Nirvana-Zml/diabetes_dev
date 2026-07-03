import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const BUSINESS = [
  'src/api/',
  'src/router/',
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
  console.log(`${normalized}: ${uncovered.join(', ')}`)
}
