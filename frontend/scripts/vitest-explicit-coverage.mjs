import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import coverage from 'istanbul-lib-coverage'
import report from 'istanbul-lib-report'
import reports from 'istanbul-reports'

const { createCoverageMap } = coverage
const { createContext } = report

const root = process.cwd()
const tests = []
const tempRoot = join(root, '.coverage-parts')
const coverageRoot = join(root, 'coverage')

function cleanupTempCoverage() {
  rmSync(tempRoot, { recursive: true, force: true })
}

process.on('exit', cleanupTempCoverage)

function collect(dir) {
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry)
    const stat = statSync(fullPath)
    if (stat.isDirectory()) {
      collect(fullPath)
      continue
    }
    if (/\.(test|spec)\.js$/.test(entry)) {
      tests.push(relative(root, fullPath).replaceAll('\\', '/'))
    }
  }
}

collect(join(root, 'src'))
tests.sort()

const localVitest = join(root, 'node_modules', '.bin', process.platform === 'win32' ? 'vitest.cmd' : 'vitest')
const command = existsSync(localVitest) ? localVitest : (process.platform === 'win32' ? 'npx.cmd' : 'npx')
const commandPrefix = existsSync(localVitest) ? [] : ['vitest']
rmSync(tempRoot, { recursive: true, force: true })
rmSync(coverageRoot, { recursive: true, force: true })
mkdirSync(tempRoot, { recursive: true })

const chunks = []
for (let index = 0; index < tests.length; index += 5) {
  chunks.push(tests.slice(index, index + 5))
}

for (const [index, chunk] of chunks.entries()) {
  const reportsDirectory = join(tempRoot, `part-${index}`)
  const result = spawnSync(command, [
    ...commandPrefix,
    'run',
    '--coverage',
    '--coverage.reporter=json',
    `--coverage.reportsDirectory=${reportsDirectory}`,
    '--coverage.thresholds.statements=0',
    '--coverage.thresholds.branches=0',
    '--coverage.thresholds.functions=0',
    '--coverage.thresholds.lines=0',
    ...chunk,
    '--reporter',
    'dot',
  ], {
    cwd: root,
    stdio: 'inherit',
    shell: false,
  })

  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

const coverageMap = createCoverageMap({})
for (let index = 0; index < chunks.length; index++) {
  const finalPath = join(tempRoot, `part-${index}`, 'coverage-final.json')
  if (existsSync(finalPath)) {
    coverageMap.merge(JSON.parse(readFileSync(finalPath, 'utf8')))
  }
}

mkdirSync(coverageRoot, { recursive: true })
writeFileSync(join(coverageRoot, 'coverage-final.json'), JSON.stringify(coverageMap.toJSON()))
writeFileSync(join(coverageRoot, 'coverage-summary.json'), JSON.stringify(coverageMap.getCoverageSummary().toJSON(), null, 2))

const context = createContext({
  dir: coverageRoot,
  coverageMap,
})

reports.create('html').execute(context)
reports.create('text').execute(context)
cleanupTempCoverage()
