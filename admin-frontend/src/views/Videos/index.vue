<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="admin-header-inner">
        <div>
          <el-button link type="primary" @click="router.push('/home')">← 返回模块首页</el-button>
          <h1>科普视频管理</h1>
        </div>
        <div class="header-actions">
          <el-button type="primary" @click="openCreate">新建视频</el-button>
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
        <el-button @click="loadList">查询</el-button>
      </div>

      <div class="section-card" v-loading="loading">
        <el-table :data="list" stripe>
          <el-table-column label="封面" width="120">
            <template #default="{ row }">
              <img
                v-if="coverUrl(row)"
                :src="coverUrl(row)"
                class="table-cover"
                alt="封面"
              />
              <span v-else class="no-cover">无封面</span>
            </template>
          </el-table-column>
          <el-table-column prop="video_id" label="视频 ID" width="120" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="时长" width="90">
            <template #default="{ row }">{{ row.duration || '—' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDate(row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!list.length && !loading" class="empty-state">暂无数据</div>
      </div>
    </main>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑视频' : '新建视频'"
      width="720px"
      align-center
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item v-if="editingId" label="视频 ID">
          <el-input :model-value="editingId" disabled />
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" placeholder="请输入视频标题" show-word-limit />
        </el-form-item>

        <el-form-item label="封面图">
          <div class="cover-field">
            <el-upload
              class="cover-uploader"
              :show-file-list="false"
              accept="image/*"
              :auto-upload="false"
              :on-change="onCoverChange"
            >
              <img v-if="coverPreview" :src="coverPreview" class="cover-preview" alt="封面预览" />
              <div v-else class="cover-placeholder">
                <el-icon><Upload /></el-icon>
                <span>点击上传封面（JPG/PNG）</span>
              </div>
            </el-upload>
            <el-button v-if="coverPreview" link type="danger" @click="clearCover">移除封面</el-button>
          </div>
        </el-form-item>

        <el-form-item label="视频文件（MP4）">
          <div class="video-field">
            <el-upload
              :show-file-list="false"
              accept="video/mp4,.mp4"
              :auto-upload="false"
              :on-change="onVideoChange"
            >
              <el-button type="primary" plain>{{ videoFile ? '重新选择' : '选择 MP4 视频' }}</el-button>
            </el-upload>
            <span v-if="videoFile" class="video-file-name">{{ videoFile.name }}</span>
            <span v-if="form.duration" class="video-duration">时长：{{ form.duration }}</span>
            <video
              v-if="videoPreviewUrl"
              :src="videoPreviewUrl"
              class="video-preview"
              controls
              preload="metadata"
            />
            <el-progress
              v-if="uploadProgress > 0 && uploadProgress < 100"
              :percentage="uploadProgress"
              :stroke-width="8"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import {
  getAdminVideos,
  getAdminVideoDetail,
  createAdminVideo,
  updateAdminVideo,
  deleteAdminVideo,
  uploadAdminVideoCover,
  uploadAdminVideoFile,
} from '@/api/video'
import { videoCoverUrl } from '@/utils/media'

const router = useRouter()

const keyword = ref('')
const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref('')
const saving = ref(false)
const uploadProgress = ref(0)

const formRef = ref(null)
const form = ref({ title: '', duration: '' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

const coverFile = ref(null)
const coverPreview = ref('')
const videoFile = ref(null)
const videoPreviewUrl = ref('')

function coverUrl(row) {
  if (row.cover_url) return row.cover_url
  return videoCoverUrl(row.video_id)
}

function formatDate(val) {
  if (!val) return '—'
  return String(val).replace('T', ' ').slice(0, 16)
}

async function loadList() {
  loading.value = true
  try {
    const { list: rows } = await getAdminVideos({ keyword: keyword.value })
    list.value = rows
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = ''
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  editingId.value = row.video_id
  resetForm()
  dialogVisible.value = true
  try {
    const detail = await getAdminVideoDetail(row.video_id)
    form.value.title = detail.title || ''
    form.value.duration = detail.duration || ''
    coverPreview.value = detail.cover_url || videoCoverUrl(detail.video_id)
    if (detail.video_url) {
      videoPreviewUrl.value = detail.video_url
    }
  } catch (e) {
    ElMessage.error(e.message || '加载详情失败')
  }
}

function onCoverChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  coverFile.value = raw
  coverPreview.value = URL.createObjectURL(raw)
}

function clearCover() {
  coverFile.value = null
  coverPreview.value = ''
}

function onVideoChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (!raw.name.toLowerCase().endsWith('.mp4')) {
    ElMessage.warning('请选择 MP4 格式视频')
    return
  }
  videoFile.value = raw
  videoPreviewUrl.value = URL.createObjectURL(raw)
  form.value.duration = ''
  uploadProgress.value = 0
}

function resetForm() {
  form.value = { title: '', duration: '' }
  coverFile.value = null
  coverPreview.value = ''
  videoFile.value = null
  videoPreviewUrl.value = ''
  uploadProgress.value = 0
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    let videoId = editingId.value
    if (videoId) {
      await updateAdminVideo(videoId, { title: form.value.title })
    } else {
      const created = await createAdminVideo({ title: form.value.title })
      videoId = created.video_id
    }

    if (coverFile.value && videoId) {
      const uploaded = await uploadAdminVideoCover(videoId, coverFile.value)
      if (uploaded.cover_url) {
        coverPreview.value = uploaded.cover_url
      }
    }

    if (videoFile.value && videoId) {
      uploadProgress.value = 0
      const uploaded = await uploadAdminVideoFile(videoId, videoFile.value, (evt) => {
        if (evt.total) {
          uploadProgress.value = Math.round((evt.loaded / evt.total) * 100)
        }
      })
      if (uploaded.duration) {
        form.value.duration = uploaded.duration
      }
      if (uploaded.video_url) {
        videoPreviewUrl.value = uploaded.video_url
      }
      uploadProgress.value = 100
    }

    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.title}」？`, '提示', { type: 'warning' })
  try {
    await deleteAdminVideo(row.video_id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: var(--warm-50);
}
.admin-header {
  background: #fff;
  border-bottom: 1px solid var(--warm-100);
  padding: 24px var(--edge-padding);
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
  margin: 0;
  font-size: 24px;
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
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
}
.section-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--warm-100);
  padding: 16px;
}
.empty-state {
  text-align: center;
  color: var(--warm-400);
  padding: 48px 0;
}
.table-cover {
  width: 96px;
  height: 54px;
  object-fit: cover;
  border-radius: 6px;
  background: var(--warm-100);
}
.no-cover {
  font-size: 12px;
  color: var(--warm-400);
}
.cover-field,
.video-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}
.cover-uploader :deep(.el-upload) {
  border: 1px dashed var(--warm-200);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
}
.cover-preview {
  width: 240px;
  height: 135px;
  object-fit: cover;
  display: block;
}
.cover-placeholder {
  width: 240px;
  height: 135px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--warm-400);
  background: var(--warm-50);
}
.video-file-name {
  font-size: 13px;
  color: var(--warm-600);
}
.video-duration {
  font-size: 13px;
  color: var(--warm-500);
}
.video-preview {
  width: 100%;
  max-width: 480px;
  border-radius: 8px;
  background: #000;
}
</style>
