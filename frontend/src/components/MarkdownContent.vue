<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({
  content: { type: String, default: '' },
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const html = computed(() => md.render(props.content || ''))
</script>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  word-break: break-word;
}

.markdown-body :deep(h2) {
  font-size: 16px;
  margin: 12px 0 8px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body :deep(strong) {
  color: #0d9488;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid #0d9488;
  background: #f0fdfa;
  color: #64748b;
}
</style>
