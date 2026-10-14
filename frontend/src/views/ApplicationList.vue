<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索应用" class="w-240" clearable @change="load" />
      <el-button type="primary" @click="dialog = true" data-testid="app-create">新建应用</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="8" v-for="a in apps" :key="a.id" style="margin-bottom: 16px">
        <div class="page-card app-card" @click="$router.push(`/applications/${a.id}`)">
          <div class="ac-head">
            <el-avatar :size="36" class="ac-avatar">{{ a.name.slice(0, 1).toUpperCase() }}</el-avatar>
            <div>
              <div class="ac-name">{{ a.name }}</div>
              <div class="ac-key">{{ a.key }} · {{ a.language }}</div>
            </div>
          </div>
          <p class="ac-desc">{{ a.description || '暂无描述' }}</p>
          <div class="ac-foot">负责人：{{ a.ownerName || '-' }}</div>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="dialog" title="新建应用" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="应用名" required><el-input v-model="form.name" data-testid="app-name" /></el-form-item>
        <el-form-item label="标识" required><el-input v-model="form.key" placeholder="如 GATEWAY" data-testid="app-key" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="仓库地址"><el-input v-model="form.repoUrl" placeholder="git@..." /></el-form-item>
        <el-form-item label="语言">
          <el-select v-model="form.language" style="width: 100%">
            <el-option value="JAVA" label="Java" /><el-option value="GOLANG" label="Go" />
            <el-option value="NODEJS" label="Node.js" /><el-option value="PYTHON" label="Python" />
            <el-option value="OTHER" label="其他" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save" data-testid="app-save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createApplication, listApplications } from '../api'

const apps = ref([])
const keyword = ref('')
const dialog = ref(false)
const form = reactive({ name: '', key: '', description: '', repoUrl: '', language: 'JAVA' })

async function load() {
  apps.value = (await listApplications({ keyword: keyword.value })).data
}

async function save() {
  if (!form.name || !form.key) {
    ElMessage.warning('请填写应用名和标识')
    return
  }
  await createApplication(form)
  ElMessage.success('创建成功')
  dialog.value = false
  Object.assign(form, { name: '', key: '', description: '', repoUrl: '', language: 'JAVA' })
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.w-240 { width: 240px; }
.app-card { cursor: pointer; transition: box-shadow 0.2s, border-color 0.2s; }
.app-card:hover { box-shadow: 0 6px 20px rgba(0, 80, 255, 0.1); border-color: #cddfff; }
.ac-head { display: flex; align-items: center; gap: 10px; }
.ac-avatar { background: #0fc6c2; }
.ac-name { font-weight: 600; color: var(--df-text); }
.ac-key { color: var(--df-text-3); font-size: var(--df-font-sm); }
.ac-desc { color: var(--df-text-2); font-size: var(--df-font); margin: 12px 0; min-height: 38px; }
.ac-foot { color: var(--df-text-3); font-size: var(--df-font-sm); }
</style>
