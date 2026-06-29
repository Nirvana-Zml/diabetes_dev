<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import multimdTable from 'markdown-it-multimd-table'

const props = defineProps({
  content: { type: String, default: '' },
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
md.use(multimdTable, { multiline: true, rowspan: true, headerless: true })

const html = computed(() => md.render(props.content || ''))
</script>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: var(--warm-800, #292524);
  word-break: break-word;
}

.markdown-body :deep(h2) {
  font-size: 17px;
  font-weight: 700;
  margin: 20px 0 10px;
  color: var(--warm-900, #1c1917);
}

.markdown-body :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
  color: var(--warm-900, #1c1917);
}

.markdown-body :deep(p) {
  margin: 0 0 12px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 22px;
  margin: 8px 0 12px;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(strong) {
  font-weight: 700;
  color: var(--warm-900, #1c1917);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--warm-200, #e7e5e4);
  margin: 20px 0;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 3px solid var(--el-color-primary, #409eff);
  background: var(--warm-50, #fafaf9);
  color: var(--warm-600, #57534e);
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0 16px;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--warm-200, #e7e5e4);
  padding: 8px 10px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--warm-100, #f5f5f4);
  font-weight: 600;
}

.markdown-body :deep(tr:nth-child(even) td) {
  background: var(--warm-50, #fafaf9);
}
</style>
