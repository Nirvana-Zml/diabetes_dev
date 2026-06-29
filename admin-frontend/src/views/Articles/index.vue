<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="admin-header-inner">
        <div>
          <el-button link type="primary" @click="router.push('/home')">← 返回模块首页</el-button>
          <h1>资讯管理</h1>
        </div>
        <div class="header-actions">
          <el-button type="primary" @click="openCreate">新建资讯</el-button>
          <el-button @click="handleAiGenerate">AI 生成初稿</el-button>
        </div>
      </div>
    </header>

    <main class="admin-main">
      <div class="toolbar section-card">
        <el-input
          v-model="keyword"
          placeholder="搜索标题"
          clearable
          style="width: 240px"
          @keyup.enter="loadList"
        />
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 140px" @change="loadList">
          <el-option v-for="(label, key) in ARTICLE_STATUS_LABELS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button @click="loadList">查询</el-button>
        <el-button type="warning" plain @click="loadPending">待审核列表</el-button>
      </div>

      <div class="section-card" v-loading="loading">
        <el-table :data="list" stripe>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column label="分类" width="120">
            <template #default="{ row }">{{ categoryMap[row.category] || row.category }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ ARTICLE_STATUS_LABELS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="view_count" label="阅读" width="80" />
          <el-table-column label="发布时间" width="120">
            <template #default="{ row }">{{ formatDate(row.published_at || row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'draft' || row.status === 'rejected'" link type="success" @click="handleSubmit(row)">
                提交审核
              </el-button>
              <template v-if="row.status === 'pending'">
                <el-button link type="success" @click="handleReview(row, 'approve')">通过</el-button>
                <el-button link type="danger" @click="openReject(row)">驳回</el-button>
              </template>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!list.length && !loading" class="empty-state">暂无数据</div>
      </div>
    </main>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑资讯' : '新建资讯'"
      width="960px"
      align-center
      destroy-on-close
      class="article-edit-dialog"
      modal-class="article-edit-overlay"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="article-edit-form"
        hide-required-asterisk
      >
        <div class="article-edit-layout">
          <div class="article-edit-meta">
            <el-form-item label="标题" prop="title" class="article-form-item">
              <template #label>
                <div class="field-label-row">
                  <span class="field-label">标题 <span class="field-required">*</span></span>
                  <span class="char-count">{{ form.title.length }} / 100</span>
                </div>
              </template>
              <el-input
                v-model="form.title"
                maxlength="100"
                placeholder="请输入资讯标题"
                class="float-input"
              />
            </el-form-item>

            <el-form-item label="摘要" prop="summary" class="article-form-item">
              <template #label>
                <div class="field-label-row">
                  <span class="field-label">摘要 <span class="field-required">*</span></span>
                  <span class="char-count">{{ form.summary.length }} / 200</span>
                </div>
              </template>
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="3"
                maxlength="200"
                placeholder="简要概括资讯核心内容"
                class="float-input float-textarea"
              />
            </el-form-item>

            <el-form-item label="分类" prop="category" class="article-form-item">
              <template #label>
                <span class="field-label">分类 <span class="field-required">*</span></span>
              </template>
              <el-select
                v-model="form.category"
                placeholder="选择分类"
                class="float-select"
                popper-class="float-select-popper"
              >
                <el-option v-for="(label, key) in categoryMap" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>

            <el-form-item label="封面图" class="article-form-item">
              <template #label>
                <span class="field-label">封面图</span>
              </template>
              <div class="cover-field">
                <el-upload
                  class="cover-dropzone"
                  :show-file-list="false"
                  accept="image/*"
                  :auto-upload="false"
                  :on-change="handleCoverChange"
                >
                  <div class="cover-dropzone-inner">
                    <img v-if="coverPreview" :src="coverPreview" class="cover-preview" alt="封面预览" />
                    <div v-else class="cover-dropzone-empty">
                      <el-icon class="cover-upload-icon"><Upload /></el-icon>
                      <span>点击上传封面图</span>
                    </div>
                  </div>
                </el-upload>
                <button v-if="coverPreview" type="button" class="cover-remove" @click="clearCover">移除</button>
              </div>
            </el-form-item>

            <el-form-item label="标签" class="article-form-item">
              <template #label>
                <span class="field-label">标签</span>
              </template>
              <el-input
                v-model="form.tagsText"
                placeholder="多个标签用逗号分隔"
                class="float-input"
              />
            </el-form-item>
          </div>

          <div class="article-edit-body">
            <el-form-item label="正文" prop="content" class="article-form-item article-form-item--content">
              <template #label>
                <span class="field-label">正文 <span class="field-required">*</span></span>
              </template>
              <div class="content-editor">
                <div class="content-mode-switch">
                  <button
                    type="button"
                    class="mode-tab"
                    :class="{ 'mode-tab--active': contentMode === 'edit' }"
                    @click="contentMode = 'edit'"
                  >
                    Markdown 编辑
                  </button>
                  <button
                    type="button"
                    class="mode-tab"
                    :class="{ 'mode-tab--active': contentMode === 'preview' }"
                    @click="contentMode = 'preview'"
                  >
                    预览
                  </button>
                </div>
                <el-input
                  v-if="contentMode === 'edit'"
                  v-model="form.content"
                  type="textarea"
                  placeholder="支持 Markdown 语法"
                  class="content-textarea"
                />
                <div v-else class="content-preview-box">
                  <MarkdownContent v-if="form.content" :content="form.content" />
                  <p v-else class="content-preview-empty">暂无正文，请切换至编辑模式输入</p>
                </div>
              </div>
            </el-form-item>
          </div>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer-minimal">
          <button type="button" class="btn-text" @click="dialogVisible = false">取消</button>
          <button type="button" class="btn-primary-soft" :disabled="saving" @click="handleSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回资讯" width="480px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="aiDialogVisible"
      title="AI 生成资讯初稿"
      width="760px"
      destroy-on-close
      :close-on-click-modal="!aiGenerating"
      :close-on-press-escape="!aiGenerating"
      @closed="resetAiDialog"
    >
      <el-form label-width="80px">
        <el-form-item label="主题" required>
          <el-input
            v-model="aiForm.topic"
            placeholder="如：糖尿病早餐饮食管理"
            maxlength="100"
            :disabled="aiGenerating"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="aiForm.keywords"
            placeholder="逗号分隔，如：糖尿病,早餐,GI值,控糖"
            maxlength="200"
            :disabled="aiGenerating"
          />
        </el-form-item>
      </el-form>

      <div v-if="aiGenerating || aiDraft.title || aiDraft.content" class="ai-preview section-card">
        <div class="ai-preview-header">
          <span>{{ aiGenerating ? '正在生成…' : '生成预览' }}</span>
          <el-tag v-if="aiGenerating" type="warning" size="small">流式输出中</el-tag>
        </div>
        <p v-if="aiDraft.title" class="ai-preview-title">{{ aiDraft.title }}</p>
        <p v-if="aiDraft.summary" class="ai-preview-summary">{{ aiDraft.summary }}</p>
        <div class="ai-preview-body">
          <MarkdownContent v-if="aiDraft.content" :content="aiDraft.content" />
          <p v-else class="content-preview-empty">正文将在此逐段显示…</p>
        </div>
        <div v-if="aiDraft.tags?.length" class="ai-preview-tags">
          <el-tag v-for="tag in aiDraft.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
        </div>
      </div>

      <template #footer>
        <el-button :disabled="aiGenerating" @click="aiDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiGenerating" @click="startAiGenerate">
          {{ aiGenerating ? '生成中…' : '开始生成' }}
        </el-button>
        <el-button
          type="success"
          :disabled="aiGenerating || !aiDraft.content"
          @click="applyAiDraft"
        >
          填入编辑表单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import MarkdownContent from '@/components/MarkdownContent.vue'
import {
  getAdminArticles,
  getAdminArticleDetail,
  createAdminArticle,
  updateAdminArticle,
  uploadAdminArticleCover,
  deleteAdminArticle,
  submitAdminArticle,
  reviewAdminArticle,
  getPendingReviewArticles,
  generateArticleDraft,
  categoryMap,
  ARTICLE_STATUS_LABELS,
} from '@/api/article'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const dialogVisible = ref(false)
const rejectVisible = ref(false)
const editingId = ref('')
const rejectReason = ref('')
const rejectTarget = ref(null)
const formRef = ref()
const coverFile = ref(null)
const coverPreview = ref('')
const contentMode = ref('edit')
const aiDialogVisible = ref(false)
const aiGenerating = ref(false)
const aiForm = reactive({ topic: '', keywords: '' })
const aiDraft = reactive({
  title: '',
  summary: '',
  content: '',
  tags: [],
})

const form = reactive({
  title: '',
  summary: '',
  category: 'diabetes_basics',
  content: '',
  tagsText: '',
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入摘要', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }],
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const data = await getAdminArticles({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
    })
    list.value = data.list
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadPending() {
  loading.value = true
  try {
    const data = await getPendingReviewArticles()
    list.value = data.list
    statusFilter.value = 'pending'
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.summary = ''
  form.category = 'diabetes_basics'
  form.content = ''
  form.tagsText = ''
  contentMode.value = 'edit'
  coverFile.value = null
  coverPreview.value = ''
}

function handleCoverChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (!raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  coverFile.value = raw
  coverPreview.value = URL.createObjectURL(raw)
}

function clearCover() {
  coverFile.value = null
  coverPreview.value = ''
}

function openCreate() {
  editingId.value = ''
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  editingId.value = row.article_id
  try {
    const detail = await getAdminArticleDetail(row.article_id)
    form.title = detail.title
    form.summary = detail.summary
    form.category = detail.category
    form.content = detail.content || ''
    form.tagsText = Array.isArray(detail.tags) ? detail.tags.join(',') : (detail.tags || '')
    contentMode.value = 'edit'
    coverFile.value = null
    coverPreview.value = detail.cover_image || ''
    dialogVisible.value = true
  } catch (err) {
    ElMessage.error(err.message || '加载详情失败')
  }
}

function buildPayload() {
  const tags = form.tagsText
    ? form.tagsText.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
    : []
  return {
    title: form.title.trim(),
    summary: form.summary.trim(),
    category: form.category,
    content: form.content,
    tags,
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = buildPayload()
    let articleId = editingId.value
    if (articleId) {
      await updateAdminArticle(articleId, payload)
    } else {
      const created = await createAdminArticle(payload)
      articleId = created.article_id
    }
    if (coverFile.value && articleId) {
      const uploaded = await uploadAdminArticleCover(articleId, coverFile.value)
      if (uploaded.cover_image) {
        coverPreview.value = uploaded.cover_image
      }
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadList()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.title}」？`, '提示', { type: 'warning' })
  try {
    await deleteAdminArticle(row.article_id)
    ElMessage.success('已删除')
    loadList()
  } catch (err) {
    ElMessage.error(err.message || '删除失败')
  }
}

async function handleSubmit(row) {
  try {
    await submitAdminArticle(row.article_id)
    ElMessage.success('已提交审核')
    loadList()
  } catch (err) {
    ElMessage.error(err.message || '提交失败')
  }
}

async function handleReview(row, action) {
  try {
    await reviewAdminArticle(row.article_id, action)
    ElMessage.success(action === 'approve' ? '已通过并发布' : '已驳回')
    loadList()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  }
}

function openReject(row) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  saving.value = true
  try {
    await reviewAdminArticle(rejectTarget.value.article_id, 'reject', rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadList()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    saving.value = false
  }
}

function handleAiGenerate() {
  aiForm.topic = ''
  aiForm.keywords = ''
  resetAiDraft()
  aiDialogVisible.value = true
}

function resetAiDraft() {
  aiDraft.title = ''
  aiDraft.summary = ''
  aiDraft.content = ''
  aiDraft.tags = []
}

function resetAiDialog() {
  if (!aiGenerating.value) resetAiDraft()
}

async function startAiGenerate() {
  if (!aiForm.topic.trim()) {
    ElMessage.warning('请输入资讯主题')
    return
  }
  aiGenerating.value = true
  resetAiDraft()
  try {
    await generateArticleDraft(
      { topic: aiForm.topic.trim(), keywords: aiForm.keywords.trim() },
      {
        onChunk: (draft) => {
          if (draft.title) aiDraft.title = draft.title
          if (draft.summary) aiDraft.summary = draft.summary
          if (draft.content != null) aiDraft.content = draft.content
          if (Array.isArray(draft.tags)) aiDraft.tags = [...draft.tags]
        },
        onComplete: (draft) => {
          if (draft.title) aiDraft.title = draft.title
          if (draft.summary) aiDraft.summary = draft.summary
          if (draft.content != null) aiDraft.content = draft.content
          if (Array.isArray(draft.tags)) aiDraft.tags = [...draft.tags]
        },
      },
    )
    ElMessage.success('AI 初稿生成完成')
  } catch (err) {
    ElMessage.error(err.message || 'AI 生成失败')
  } finally {
    aiGenerating.value = false
  }
}

function applyAiDraft() {
  if (!aiDraft.content) {
    ElMessage.warning('请先生成初稿')
    return
  }
  editingId.value = ''
  form.title = aiDraft.title
  form.summary = aiDraft.summary
  form.content = aiDraft.content
  form.tagsText = Array.isArray(aiDraft.tags) ? aiDraft.tags.join(',') : ''
  form.category = 'diet'
  contentMode.value = 'preview'
  coverFile.value = null
  coverPreview.value = ''
  aiDialogVisible.value = false
  dialogVisible.value = true
  ElMessage.success('已填入编辑表单，请核对后保存')
}

function statusTagType(status) {
  return { draft: 'info', pending: 'warning', published: 'success', rejected: 'danger' }[status] || 'info'
}

function formatDate(d) {
  return d ? dayjs(d).format('YYYY-MM-DD') : '—'
}
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: var(--warm-50);
}
.admin-header {
  background: #fff;
  border-bottom: 1px solid var(--warm-100);
  padding: 20px var(--edge-padding);
}
.admin-header-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.admin-header h1 {
  margin: 8px 0 0;
  font-size: 22px;
  color: var(--warm-800);
}
.header-actions {
  display: flex;
  gap: 10px;
}
.admin-main {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 24px var(--edge-padding) 64px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 20px !important;
}
.section-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--warm-100);
  padding: 20px;
}
.empty-state {
  text-align: center;
  padding: 32px;
  color: var(--warm-400);
}

/* —— 编辑资讯弹窗：医疗风极简 —— */
.article-edit-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 36px;
  align-items: stretch;
}

.article-edit-meta {
  min-width: 0;
}

.article-edit-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.article-edit-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.article-edit-form :deep(.el-form-item__content) {
  width: 100%;
  min-width: 0;
}

.article-form-item--content {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 0 !important;
}

.article-form-item--content :deep(.el-form-item__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.article-edit-form :deep(.el-form-item__label) {
  padding: 0 0 8px;
  line-height: 1.4;
  width: 100% !important;
}

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #57534e;
  letter-spacing: 0.02em;
}

.field-required {
  color: #d4a0a0;
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
}

.char-count {
  flex-shrink: 0;
  font-size: 11px;
  color: #c4bfba;
  letter-spacing: 0.02em;
}

.float-input {
  width: 100%;
}

.float-input :deep(.el-input),
.float-input :deep(.el-textarea) {
  width: 100%;
}

.float-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  border: none !important;
  border-bottom: 1px solid #e7e5e4 !important;
  border-radius: 0 !important;
  background: transparent !important;
  padding: 6px 0 10px !important;
  transition: border-color 0.2s ease;
}

.float-input :deep(.el-input__wrapper:hover),
.float-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none !important;
  border-bottom-color: #a8a29e !important;
}

.float-input :deep(.el-input__inner) {
  color: #292524;
  font-size: 14px;
}

.float-input :deep(.el-input__inner::placeholder) {
  color: #c4bfba;
}

.float-textarea :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: none !important;
  border-bottom: 1px solid #e7e5e4 !important;
  border-radius: 0 !important;
  background: transparent !important;
  padding: 8px 0 10px !important;
  line-height: 1.75;
  color: #292524;
  resize: vertical;
  min-height: 72px !important;
}

.float-textarea :deep(.el-textarea__inner:hover),
.float-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: none !important;
  border-bottom-color: #a8a29e !important;
}

.float-textarea :deep(.el-textarea__inner::placeholder) {
  color: #c4bfba;
}

.float-select {
  width: 100%;
}

.float-select :deep(.el-select__wrapper) {
  box-shadow: none !important;
  border: none !important;
  border-bottom: 1px solid #e7e5e4 !important;
  border-radius: 6px !important;
  background: #fafaf9 !important;
  padding: 6px 8px 10px 0 !important;
  min-height: 36px;
  transition: border-color 0.2s ease;
}

.float-select :deep(.el-select__wrapper:hover),
.float-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: none !important;
  border-bottom-color: #a8a29e !important;
}

.float-select :deep(.el-select__placeholder) {
  color: #c4bfba;
}

.float-select :deep(.el-select__suffix) {
  opacity: 0.35;
}

.cover-field {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.cover-dropzone :deep(.el-upload) {
  display: block;
  width: 100%;
}

.cover-dropzone-inner {
  width: 100%;
  min-height: 120px;
  border: 1px dashed #d6d3d1;
  border-radius: 12px;
  background: #fafaf9;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-dropzone :deep(.el-upload:hover) .cover-dropzone-inner {
  border-color: #a8a29e;
  background: #f5f5f4;
}

.cover-dropzone-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  min-height: 120px;
  color: #a8a29e;
  font-size: 13px;
}

.cover-upload-icon {
  font-size: 22px;
  color: #c4bfba;
  stroke-width: 1.2;
}

.cover-preview {
  width: 100%;
  height: 120px;
  object-fit: cover;
  display: block;
}

.cover-remove {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: #d4a0a0;
  cursor: pointer;
  transition: color 0.15s ease;
}

.cover-remove:hover {
  color: #c08080;
}

.content-editor {
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 480px;
}

.content-mode-switch {
  display: flex;
  gap: 20px;
  margin-bottom: 14px;
  border-bottom: 1px solid #f0eeec;
}

.mode-tab {
  position: relative;
  border: none;
  background: none;
  padding: 0 0 10px;
  font-size: 13px;
  color: #a8a29e;
  cursor: pointer;
  transition: color 0.2s ease;
}

.mode-tab:hover {
  color: #78716c;
}

.mode-tab--active {
  color: #44403c;
  font-weight: 500;
}

.mode-tab--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #0d9488;
  border-radius: 1px;
}

.content-textarea {
  flex: 1;
  width: 100%;
}

.content-textarea :deep(.el-textarea) {
  height: 100%;
}

.content-textarea :deep(.el-textarea__inner) {
  background: #f5f5f4 !important;
  border: none !important;
  border-radius: 12px !important;
  box-shadow: none !important;
  padding: 18px 20px !important;
  line-height: 1.85;
  font-size: 14px;
  color: #292524;
  resize: none;
  height: 100% !important;
  min-height: 420px !important;
}

.content-textarea :deep(.el-textarea__inner::placeholder) {
  color: #c4bfba;
}

.content-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: none !important;
  outline: none;
  background: #f0eeec !important;
}

.content-preview-box {
  flex: 1;
  min-height: 420px;
  max-height: none;
  overflow-y: auto;
  padding: 18px 20px;
  border-radius: 12px;
  background: #f5f5f4;
}

.content-preview-empty {
  margin: 0;
  color: #c4bfba;
  font-size: 13px;
}

@media (max-width: 860px) {
  .article-edit-layout {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .content-editor {
    min-height: 360px;
  }

  .content-textarea :deep(.el-textarea__inner),
  .content-preview-box {
    min-height: 300px !important;
  }
}

.dialog-footer-minimal {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 20px;
}

.btn-text {
  border: none;
  background: none;
  padding: 8px 4px;
  font-size: 14px;
  color: #a8a29e;
  cursor: pointer;
  transition: color 0.15s ease;
}

.btn-text:hover {
  color: #78716c;
}

.btn-primary-soft {
  border: none;
  background: rgba(13, 148, 136, 0.12);
  color: #0d9488;
  padding: 10px 28px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.btn-primary-soft:hover:not(:disabled) {
  background: rgba(13, 148, 136, 0.2);
  color: #0f766e;
}

.btn-primary-soft:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ai-preview {
  margin-top: 8px;
  padding: 16px !important;
  background: var(--warm-50);
}
.ai-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 600;
  color: var(--warm-700);
}
.ai-preview-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--warm-800);
}
.ai-preview-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--warm-500);
  line-height: 1.6;
}
.ai-preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.ai-preview-body {
  max-height: 420px;
  overflow-y: auto;
  padding: 12px 4px;
  border: 1px solid var(--warm-100);
  border-radius: 8px;
  background: #fff;
}
</style>

<style>
/* 弹窗遮罩与容器（需穿透 scoped） */
.article-edit-overlay {
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  background-color: rgba(41, 37, 36, 0.1) !important;
}

.article-edit-dialog.el-dialog {
  border-radius: 18px !important;
  box-shadow: 0 12px 40px rgba(41, 37, 36, 0.07) !important;
  padding: 0 !important;
  overflow: hidden;
}

.article-edit-dialog .el-dialog__header {
  padding: 28px 32px 4px !important;
  margin-right: 0 !important;
  border: none !important;
}

.article-edit-dialog .el-dialog__title {
  font-size: 17px;
  font-weight: 600;
  color: #292524;
  letter-spacing: 0.02em;
}

.article-edit-dialog .el-dialog__headerbtn {
  top: 22px;
  right: 22px;
  width: 28px;
  height: 28px;
}

.article-edit-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #c4bfba;
  font-size: 16px;
}

.article-edit-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #78716c;
}

.article-edit-dialog .el-dialog__body {
  padding: 12px 32px 8px !important;
}

.article-edit-dialog .el-dialog__footer {
  padding: 12px 32px 28px !important;
  border: none !important;
}

.float-select-popper.el-popper {
  border: 1px solid #f0eeec !important;
  border-radius: 10px !important;
  box-shadow: 0 6px 24px rgba(41, 37, 36, 0.06) !important;
}

.float-select-popper .el-select-dropdown__item {
  font-size: 13px;
  color: #57534e;
}

.float-select-popper .el-select-dropdown__item.is-selected {
  color: #0d9488;
  font-weight: 500;
}
</style>
