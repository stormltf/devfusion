import { expect, test } from '@playwright/test'
import { ADMIN, loginAs, expectToast, uniqueName, api } from './helpers'

test.describe('认证模块', () => {
  test('登录页正常渲染', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByTestId('login-username')).toBeVisible()
    await expect(page.getByTestId('login-submit')).toBeVisible()
  })

  test('错误密码登录被拒绝', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-username').fill(ADMIN.username)
    await page.getByTestId('login-password').fill('wrong-password')
    await page.getByTestId('login-submit').click()
    await expectToast(page, '用户名或密码错误')
    await expect(page).toHaveURL(/\/login/)
  })

  test('admin 登录成功进入总览', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-username').fill(ADMIN.username)
    await page.getByTestId('login-password').fill(ADMIN.password)
    await page.getByTestId('login-submit').click()
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })
    await expect(page.locator('.logo')).toHaveText('DevFusion')
  })

  test('未登录访问受保护路由跳转登录页', async ({ page }) => {
    await page.goto('/projects')
    await expect(page).toHaveURL(/\/login/)
  })

  test('普通用户 zhangwei 可登录且看不到用户管理菜单', async ({ page }) => {
    await loginAs(page, { username: 'zhangwei', password: ADMIN.password })
    await page.goto('/dashboard')
    await expect(page.locator('.logo')).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '用户管理' })).toHaveCount(0)
  })

  test('退出登录后回到登录页', async ({ page }) => {
    await loginAs(page)
    await page.goto('/dashboard')
    await page.locator('.el-avatar').first().click()
    await page.getByRole('menuitem', { name: '退出登录' }).click()
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 })
  })

  test('登录后 /api/auth/me 返回当前用户', async ({ page }) => {
    const data = await loginAs(page)
    const me = await api(page, 'GET', '/api/auth/me', {
      headers: { Authorization: `Bearer ${data.token}` },
    })
    const body = await me.json()
    expect(body.code).toBe(0)
    expect(body.data.username).toBe(ADMIN.username)
    expect(body.data.password).toBeUndefined()
  })
})
