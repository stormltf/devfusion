import { expect } from '@playwright/test'

export const ADMIN = { username: 'admin', password: 'admin123' }

/** API 登录并把 token 注入 localStorage 与 context 默认 header，返回 { token, userId } */
export async function loginAs(page, { username = ADMIN.username, password = ADMIN.password } = {}) {
  const resp = await page.request.post('/api/auth/login', { data: { username, password } })
  expect(resp.ok()).toBeTruthy()
  const body = await resp.json()
  expect(body.code).toBe(0)
  pageTokens.set(page, body.data.token)
  await page.setExtraHTTPHeaders({ Authorization: `Bearer ${body.data.token}` })
  await page.goto('/login')
  await page.evaluate((t) => localStorage.setItem('df_token', t), body.data.token)
  return body.data
}

const pageTokens = new WeakMap()

/** 带 token 的 API 调用：api(page, 'GET', url) / api(page, 'POST', url, { data }) */
export function api(page, method, url, options = {}) {
  return page.request[method.toLowerCase()](url, {
    ...options,
    headers: { ...(options.headers || {}), Authorization: `Bearer ${pageTokens.get(page)}` },
  })
}

/** 打开 el-select 并按文本选中选项（用 aria-controls 精确锁定展开的下拉，避免隐藏下拉干扰） */
export async function pickOption(page, combo, text) {
  await combo.click()
  const ctrl = await combo.locator('input').getAttribute('aria-controls')
  await page.locator(`#${ctrl} li[role=option]`, { hasText: text }).first().click()
}

/** 确认页面 toast 提示出现 */
export async function expectToast(page, text) {
  await expect(page.locator('.el-message').filter({ hasText: text }).first()).toBeVisible({ timeout: 10_000 })
}

/** 完整表单对话框里的确认按钮 */
export async function submitDialog(page, testid) {
  await page.getByTestId(testid).click()
}

export function uniqueName(prefix) {
  return `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`
}
