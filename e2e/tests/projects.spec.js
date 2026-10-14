import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('项目管理', () => {
  let projectName, projectKey

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('创建新项目并出现在卡片列表', async ({ page }) => {
    projectName = uniqueName('电商中台')
    projectKey = projectName.replace(/[^a-zA-Z0-9一-龥]/g, '').slice(0, 6).toUpperCase() + Math.floor(Math.random() * 900 + 100)
    await page.goto('/projects')
    await page.getByTestId('project-create').click()
    await page.getByTestId('project-name').fill(projectName)
    await page.getByTestId('project-key').fill(projectKey)
    await page.getByTestId('project-save').click()
    await expectToast(page, '创建成功')
    await expect(page.locator('.project-card', { hasText: projectName })).toBeVisible()
  })

  test('项目详情进入需求页，工作流状态已初始化', async ({ page }) => {
    await page.goto('/projects')
    await page.locator('.project-card', { hasText: projectName }).click()
    await expect(page).toHaveURL(/\/projects\/\d+\/story/)
    // 新建一条需求验证编号前缀与初始状态
    await page.getByTestId('item-create-story').click()
    await page.getByTestId('item-title-story').fill('商品详情页重构')
    await page.getByTestId('item-save-story').click()
    await expectToast(page, '创建成功')
    await expect(page.getByTestId('item-table-story')).toContainText(`${projectKey}-STORY-1`)
    await expect(page.getByTestId('item-table-story')).toContainText('待处理')
  })

  test('重复项目标识被拒绝', async ({ page }) => {
    await page.goto('/projects')
    await page.getByTestId('project-create').click()
    await page.getByTestId('project-name').fill('另一个项目')
    await page.getByTestId('project-key').fill(projectKey)
    await page.getByTestId('project-save').click()
    await expectToast(page, '项目标识已存在')
  })

  test('添加项目成员并可调整角色', async ({ page }) => {
    await page.goto('/projects')
    await page.locator('.project-card', { hasText: projectName }).click()
    await page.getByTestId('proj-nav-member').click()
    await page.getByTestId('member-add').click()
    await pickOption(page, page.getByTestId('member-user-select'), '李娜')
    await page.getByTestId('member-save').click()
    await expectToast(page, '已添加')
    await expect(page.getByTestId('member-table')).toContainText('李娜')

    // 角色改为管理员
    const row = page.locator('tr', { hasText: '李娜' }).first()
    await pickOption(page, row.locator('.el-select'), '项目管理员')
    await expectToast(page, '已更新')

    // 创建者角色不可修改（OWNER select disabled；第一行必为 OWNER）
    const ownerRow = page.getByTestId('member-table').locator('tbody tr').first()
    await expect(ownerRow.locator('.el-select input')).toBeDisabled()
  })

  test('移除项目成员', async ({ page }) => {
    await page.goto('/projects')
    await page.locator('.project-card', { hasText: projectName }).click()
    await page.getByTestId('proj-nav-member').click()
    await page.locator('tr', { hasText: '李娜' }).first().getByRole('button', { name: '移除' }).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '已移除')
    await expect(page.getByTestId('member-table')).not.toContainText('李娜')
  })

  test('删除项目', async ({ page }) => {
    await page.goto('/projects')
    // 先在项目里放一个工作项，确认逻辑删除后列表消失
    await page.locator('.project-card', { hasText: projectName }).click()
    await expect(page).toHaveURL(/\/projects\/\d+/, { timeout: 10_000 })
    const projectId = page.url().match(/\/projects\/(\d+)/)[1]
    await page.goto('/projects')
    const resp = await api(page, 'DELETE', `/api/projects/${projectId}`)
    expect((await resp.json()).code).toBe(0)
    await page.reload()
    await expect(page.locator('.project-card', { hasText: projectName })).toHaveCount(0)
  })
})
