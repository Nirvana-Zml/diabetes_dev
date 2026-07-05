import { readFileSync, existsSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const root = join(process.cwd())
const summaryPath = join(root, 'coverage', 'coverage-summary.json')

const CORE_PREFIXES = [
  '/src/api/',
  '/src/utils/',
  '/src/router/',
  '/src/config/',
  '/src/mock/',
  '/src/components/',
]

const CORE_FILES = [
  '/src/App.vue',
  '/src/main.js',
]

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

const total = summary.total
const coreEntries = entries.filter(({ key }) =>
  CORE_PREFIXES.some((prefix) => key.includes(prefix)) || CORE_FILES.includes(key))
const viewEntries = entries.filter(({ key }) => key.includes('/src/views/') && key.endsWith('.vue'))

const core = aggregate(coreEntries.map(({ value }) => value))
const views = aggregate(viewEntries.map(({ value }) => value))

const totalLines = total.lines.pct
const totalBranches = total.branches.pct
const totalFunctions = total.functions.pct
const coreLines = pct(core.lines.covered, core.lines.total)
const coreBranches = pct(core.branches.covered, core.branches.total)
const viewLines = pct(views.lines.covered, views.lines.total)
const viewBranches = pct(views.branches.covered, views.branches.total)

const failures = []
if (totalLines < 100) failures.push(`全量行覆盖率 ${formatPct(totalLines)} < 100%`)
if (totalBranches < 100) failures.push(`全量分支覆盖率 ${formatPct(totalBranches)} < 100%`)
if (coreLines < 100) failures.push(`核心模块行覆盖率 ${formatPct(coreLines)} < 100%`)
if (coreBranches < 100) failures.push(`核心模块分支覆盖率 ${formatPct(coreBranches)} < 100%`)
if (viewLines < 100) failures.push(`页面行覆盖率 ${formatPct(viewLines)} < 100%`)
if (viewBranches < 100) failures.push(`页面分支覆盖率 ${formatPct(viewBranches)} < 100%`)

console.log('Coverage gate summary')
console.log(`- 全量: lines ${formatPct(totalLines)}, branches ${formatPct(totalBranches)}, functions ${formatPct(totalFunctions)} (${entries.length} files)`)
console.log(`- 核心模块: lines ${formatPct(coreLines)}, branches ${formatPct(coreBranches)} (${coreEntries.length} files)`)
console.log(`- 页面组件: lines ${formatPct(viewLines)}, branches ${formatPct(viewBranches)} (${viewEntries.length} files)`)

const lowFiles = entries
  .filter(({ value }) => value.lines.pct < 100 || value.branches.pct < 100)
  .sort((a, b) => a.value.branches.pct - b.value.branches.pct)

if (lowFiles.length) {
  console.log('\n未达 100% 的文件:')
  for (const { key, value } of lowFiles) {
    console.log(`  ${key}: lines ${value.lines.pct}%, branches ${value.branches.pct}%`)
  }
}

if (failures.length) {
  console.error('\nCoverage gate failed:')
  failures.forEach((item) => console.error(`- ${item}`))
} else {
  console.log('\nCoverage gate passed.')
}

const reportPath = join(root, 'coverage', 'TEST_REPORT.md')
const now = new Date().toISOString()
const reportLines = [
  '# admin-frontend 测试覆盖率报告',
  '',
  `- 生成时间: ${now}`,
  `- 测试命令: npm run test:coverage:gate`,
  '',
  '## 汇总',
  '',
  '| 模块 | 行覆盖率 | 分支覆盖率 | 文件数 | 门槛 |',
  '| --- | --- | --- | --- | --- |',
  `| 全量 | ${formatPct(totalLines)} | ${formatPct(totalBranches)} | ${entries.length} | 100% |`,
  `| 核心模块 (api/utils/router/...) | ${formatPct(coreLines)} | ${formatPct(coreBranches)} | ${coreEntries.length} | 100% |`,
  `| 页面组件 (views) | ${formatPct(viewLines)} | ${formatPct(viewBranches)} | ${viewEntries.length} | 100% |`,
  `| 函数覆盖率 (参考) | — | — | — | ${formatPct(totalFunctions)} |`,
  '',
  '## 结果',
  '',
  failures.length ? '- 状态: **未通过**' : '- 状态: **通过**',
  '',
]

if (failures.length) {
  reportLines.push('## 未满足门槛', '')
  failures.forEach((item) => reportLines.push(`- ${item}`))
  reportLines.push('')
}

if (lowFiles.length) {
  reportLines.push('## 未达标文件', '')
  for (const { key, value } of lowFiles) {
    reportLines.push(`- \`${key}\`: lines ${value.lines.pct}%, branches ${value.branches.pct}%`)
  }
  reportLines.push('')
}

reportLines.push('## 报告文件', '', '- HTML: `coverage/index.html`', '- JSON: `coverage/coverage-summary.json`', '')
writeFileSync(reportPath, `${reportLines.join('\n')}\n`, 'utf8')
console.log(`Report written to ${reportPath}`)

if (failures.length) {
  process.exit(1)
}
