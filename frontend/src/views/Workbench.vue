<template>
  <div class="wb">
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="page-card">
          <div class="wb-card-head">待我审批</div>
          <el-table :data="wb.pendingApprovals || []" size="small" data-testid="wb-approvals">
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ row.kind === 'JOB' ? '流水线审批' : '部署单审批' }}</template>
            </el-table-column>
            <el-table-column prop="title" label="事项" min-width="200" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="$router.push(row.link)">去处理</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(wb.pendingApprovals || []).length" description="暂无待办审批" :image-size="60" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-card">
          <div class="wb-card-head">指派给我的工作项</div>
          <el-table :data="wb.myItems || []" size="small" data-testid="wb-items">
            <el-table-column prop="no" label="编号" width="130" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag size="small" type="info">{{ row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(wb.myItems || []).length" description="暂无指派" :image-size="60" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <div class="page-card">
          <div class="wb-card-head">我发起的最近构建</div>
          <el-table :data="wb.myRuns || []" size="small" data-testid="wb-runs" @row-click="(r) => $router.push(`/runs/${r.id}`)">
            <el-table-column prop="version" label="版本" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small" :type="runType(row.status)">{{ runLabel(row.status) }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-card">
          <div class="wb-card-head">我的最近部署</div>
          <el-table :data="wb.myDeployments || []" size="small" data-testid="wb-deploys" @row-click="$router.push('/deployments')">
            <el-table-column prop="version" label="版本" min-width="180" show-overflow-tooltip />
            <el-table-column prop="deployType" label="类型" width="80">
              <template #default="{ row }">{{ row.deployType === 'ROLLBACK' ? '回滚' : '部署' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small" :type="runType(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getWorkbench } from '../api'

const wb = ref({})
const runLabel = (s) => ({ PENDING: '排队', RUNNING: '进行中', SUCCESS: '成功', FAILED: '失败', CANCELED: '取消', PENDING_APPROVAL: '待审批', DEPLOYING: '部署中' }[s] || s)
const runType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', DEPLOYING: 'warning' }[s] || 'info')

onMounted(async () => {
  wb.value = (await getWorkbench()).data
})
</script>

<style scoped>
.wb-card-head { font-weight: 600; color: var(--df-text); margin-bottom: 12px; font-size: var(--df-font-lg); }
</style>
