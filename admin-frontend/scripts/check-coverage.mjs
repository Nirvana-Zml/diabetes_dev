import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const root = join(process.cwd())
const summaryPath = join(root, 'coverage', 'coverage-summary.json')

const BUSINESS_PREFIXES = [
  '/src/api/',
  '/src/router/',
]

const UTIL_PREFIX = '/src/utils/'

function normalizePath(filePath) {
  return filePath.replace(/^\/app/, '').replace(/\\/g, '/')
}

function aggregate(entries) {
  return entries.reduce((acc, item) => {
    acc.lines.total += item.lines.total
    acc.lines.covered += item.lines.covered
    acc.branches.total += item.branches.total
    acc.branches.covered += item.branches.covered
    return acc
  }, {
    lines: { total: 0, covered: 0 },
    branches: { total: 0, covered: 0 },
  })
}

function pct(covered, total) {
  return total ? (covered / total) * 100 : 100
}

function formatPct(value) {
  return `${value.toFixed(2)}%`
}

if (!existsSync(summaryPath)) {
  console.error('Missing coverage summary. Run npm run test:coverage -- --run first.')
  process.exit(1)
}

const summary = JSON.parse(readFileSync(summaryPath, 'utf8'))
const entries = Object.entries(summary)
  .filter(([key]) => key !== 'total')
  .map(([key, value]) => ({ key: normalizePath(key), value }))

const businessEntries = entries.filter(({ key }) =>
  BUSINESS_PREFIXES.some((prefix) => key.includes(prefix)))

const utilEntries = entries.filter(({ key }) => key.includes(UTIL_PREFIX))

const business = aggregate(businessEntries.map(({ value }) => value))
const utils = aggregate(utilEntries.map(({ value }) => value))

const businessLines = pct(business.lines.covered, business.lines.total)
const businessBranches = pct(business.branches.covered, business.branches.total)
const utilLines = pct(utils.lines.covered, utils.lines.total)
const utilBranches = pct(utils.branches.covered, utils.branches.total)

const failures = []
if (businessLines < 100) failures.push(`核心业务行覆盖率 ${formatPct(businessLines)} < 100%`)
if (businessBranches < 100) failures.push(`核心业务分支覆盖率 ${formatPct(businessBranches)} < 100%`)
if (utilLines < 80) failures.push(`工具类行覆盖率 ${formatPct(utilLines)} < 80%`)
if (utilBranches < 80) failures.push(`工具类分支覆盖率 ${formatPct(utilBranches)} < 80%`)

console.log('Coverage gate summary')
console.log(`- 核心业务: lines ${formatPct(businessLines)}, branches ${formatPct(businessBranches)} (${businessEntries.length} files)`)
console.log(`- 工具类: lines ${formatPct(utilLines)}, branches ${formatPct(utilBranches)} (${utilEntries.length} files)`)

const lowBusiness = businessEntries
  .filter(({ value }) => value.lines.pct < 100 || value.branches.pct < 100)
  .sort((a, b) => a.value.lines.pct - b.value.lines.pct)

if (lowBusiness.length) {
  console.log('\n未达 100% 的核心业务文件:')
  for (const { key, value } of lowBusiness.slice(0, 20)) {
    console.log(`  ${key}: lines ${value.lines.pct}%, branches ${value.branches.pct}%`)
  }
}

const lowUtils = utilEntries.filter(({ value }) => value.lines.pct < 80 || value.branches.pct < 80)
if (lowUtils.length) {
  console.log('\n未达 80% 的工具类文件:')
  for (const { key, value } of lowUtils) {
    console.log(`  ${key}: lines ${value.lines.pct}%, branches ${value.branches.pct}%`)
  }
}

if (failures.length) {
  console.error('\nCoverage gate failed:')
  failures.forEach((item) => console.error(`- ${item}`))
  process.exit(1)
}

console.log('\nCoverage gate passed.')
