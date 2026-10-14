import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('用户管理（仅管理员）', () => {
  let username

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('新建用户出现在列表', async ({ page }) => {
    username = uniqueName('tester')
    await page.goto('/admin/users')
    await page.getByTestId('user-create').click()
    await page.getByTestId('user-username').fill(username)
    await page.getByTestId('user-password').fill('pass123456')
    await page.getByTestId('user-nickname').fill('测试用户')
    await page.getByTestId('user-save').click()
    await expectToast(page, '已保存')
    await expect(page.getByTestId('user-table')).toContainText(username)
  })

  test('编辑用户昵称', async ({ page }) => {
    await page.goto('/admin/users')
    const row = page.locator('tr', { hasText: username }).first()
    await row.getByRole('button', { name: '编辑' }).click()
    await page.getByTestId('user-nickname').fill('测试用户改')
    await page.getByTestId('user-save').click()
    await expectToast(page, '已保存')
    await expect(page.getByTestId('user-table')).toContainText('测试用户改')
  })

  test('禁用后该用户无法登录', async ({ page }) => {
    await page.goto('/admin/users')
    const row = page.locator('tr', { hasText: username }).first()
    await row.getByRole('button', { name: '禁用' }).click()
    await expectToast(page, '已禁用')

    // 登出后用该用户登录
    await api(page, 'POST', '/api/auth/logout')
    const resp = await api(page, 'POST', '/api/auth/login', {
      data: { username, password: 'pass123456' },
    })
    const body = await resp.json()
    expect(body.code).toBe(403)
    expect(body.msg).toContain('禁用')
  })

  test('重置密码后可正常登录', async ({ page }) => {
    await page.goto('/admin/users')
    const row = page.locator('tr', { hasText: username }).first()
    await row.getByRole('button', { name: '启用' }).click()
    await expectToast(page, '已启用')
    await row.getByRole('button', { name: '重置密码' }).click()
    await page.locator('.el-message-box__input input').fill('newpass123')
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '密码已重置')

    const resp = await api(page, 'POST', '/api/auth/login', {
      data: { username, password: 'newpass123' },
    })
    expect((await resp.json()).code).toBe(0)
  })

  test('删除用户后从列表消失', async ({ page }) => {
    await page.goto('/admin/users')
    const row = page.locator('tr', { hasText: username }).first()
    await row.getByRole('button', { name: '删除' }).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '已删除')
    await expect(page.getByTestId('user-table')).not.toContainText(username)
  })

  test('超级管理员不可删除/禁用', async ({ page }) => {
    await page.goto('/admin/users')
    const adminRow = page.locator('tr', { hasText: '管理员' }).first()
    await adminRow.getByRole('button', { name: '删除' }).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '不能删除超级管理员')
  })

  test('普通成员访问用户管理接口被拒', async ({ page }) => {
    await loginAs(page, { username: 'zhangwei', password: 'admin123' })
    const resp = await api(page, 'GET', '/api/users')
    const body = await resp.json()
    expect(body.code).toBe(403)
  })
})
