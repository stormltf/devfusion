<template>
  <div class="login-bg">
    <div class="login-card">
      <div class="login-brand">
        <span class="login-logo">D</span>
        <h2>DevFusion 研发管理平台</h2>
      </div>
      <el-form :model="form" label-width="0" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" data-testid="login-username" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password data-testid="login-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit" data-testid="login-submit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <p class="login-tip">演示账号：admin / admin123</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const resp = await login(form)
    localStorage.setItem('df_token', resp.data.token)
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f5ff 0%, #e6f0ff 55%, #f5f8ff 100%);
}
.login-card {
  width: 384px;
  padding: 40px 36px 28px;
  background: #fff;
  border: 1px solid var(--df-border-light);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 60, 255, 0.08);
}
.login-brand { text-align: center; margin-bottom: 26px; }
.login-logo {
  display: inline-flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 11px;
  background: var(--df-primary); color: #fff; font-size: 22px; font-weight: 700;
  margin-bottom: 12px;
}
.login-brand h2 { font-size: 18px; font-weight: 600; color: var(--df-text); }
.login-btn { width: 100%; letter-spacing: 4px; }
.login-tip { color: var(--df-text-3); font-size: var(--df-font-sm); text-align: center; margin-top: 6px; }
</style>
