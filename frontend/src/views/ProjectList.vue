<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索项目"
        class="w-240"
        clearable
        data-testid="project-search"
        @change="load"
      />
      <el-button type="primary" @click="dialog = true" data-testid="project-create">新建项目</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="8" v-for="p in projects" :key="p.id" style="margin-bottom: 16px">
        <div class="page-card project-card" @click="$router.push(`/projects/${p.id}/story`)">
          <div class="pc-head">
            <el-avatar :size="36" :style="{ background: p.color || '#0064ff' }">{{ p.name.slice(0, 1) }}</el-avatar>
            <div class="pc-titles">
              <div class="pc-name">{{ p.name }}</div>
              <div class="pc-key">{{ p.key }}</div>
            </div>
            <el-tag size="small" :type="p.status === 'ACTIVE' ? 'success' : 'info'">
              {{ { ACTIVE: '进行中', CLOSED: '已结束', ARCHIVED: '已归档' }[p.status] }}
            </el-tag>
          </div>
          <p class="pc-desc">{{ p.description || '暂无描述' }}</p>
          <div class="pc-foot">
            <span>负责人：{{ p.ownerName || '-' }}</span>
            <span>{{ p.memberCount }} 名成员</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="dialog" title="新建项目" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="项目名称" required><el-input v-model="form.name" data-testid="project-name" /></el-form-item>
        <el-form-item label="项目标识" required>
          <el-input v-model="form.key" placeholder="如 GWP，用于工作项编号前缀" data-testid="project-key" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save" data-testid="project-save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject, listProjects } from '../api'

const projects = ref([])
const keyword = ref('')
const dialog = ref(false)
const form = reactive({ name: '', key: '', description: '', color: '#0064ff' })

async function load() {
  const resp = await listProjects({ keyword: keyword.value })
  projects.value = resp.data
}

async function save() {
  if (!form.name || !form.key) {
    ElMessage.warning('请填写项目名称和标识')
    return
  }
  await createProject(form)
  ElMessage.success('创建成功')
  dialog.value = false
  form.name = ''
  form.key = ''
  form.description = ''
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.w-240 { width: 240px; }
.project-card { cursor: pointer; transition: box-shadow 0.2s, border-color 0.2s; }
.project-card:hover { box-shadow: 0 6px 20px rgba(0, 80, 255, 0.1); border-color: #cddfff; }
.pc-head { display: flex; align-items: center; gap: 10px; }
.pc-titles { min-width: 0; }
.pc-name { font-weight: 600; color: var(--df-text); }
.pc-key { color: var(--df-text-3); font-size: var(--df-font-sm); }
.pc-head .el-tag { margin-left: auto; }
.pc-desc { color: var(--df-text-2); font-size: var(--df-font); margin: 12px 0; min-height: 38px; }
.pc-foot { color: var(--df-text-3); font-size: var(--df-font-sm); display: flex; justify-content: space-between; }
</style>
