import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('工作流状态配置', () => {
  let statusName

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('项目设置显示三类工作流的默认状态', async ({ page }) => {
    await page.goto('/projects/1/settings')
    await expect(page.getByTestId('status-table-STORY')).toContainText('待处理')
    await expect(page.getByTestId('status-table-TASK')).toContainText('待办')
    await expect(page.getByTestId('status-table-BUG')).toContainText('新建')
    // 初始状态无删除按钮
    const firstRow = page.getByTestId('status-table-STORY').locator('tbody tr').first()
    await expect(firstRow).toContainText('初始')
    await expect(firstRow.getByRole('button', { name: '删除' })).toHaveCount(0)
  })

  test('新增自定义状态', async ({ page }) => {
    statusName = uniqueName('待验证')
    await page.goto('/projects/1/settings')
    await page.getByTestId('status-add-STORY').click()
    await page.getByTestId('status-name').fill(statusName)
    await page.locator('.el-dialog .el-radio', { hasText: '进行中' }).click()
    await page.getByTestId('status-save').click()
    await expectToast(page, '已添加')
    await expect(page.getByTestId('status-table-STORY')).toContainText(statusName)
  })

  test('删除无工作项使用的状态', async ({ page }) => {
    await page.goto('/projects/1/settings')
    const resp = await api(page, 'GET', '/api/projects/1/statuses')
    const list = (await resp.json()).data
    const target = list.find((s) => s.name === statusName)
    const del = await api(page, 'DELETE', `/api/projects/1/statuses/${target.id}`)
    expect((await del.json()).code).toBe(0)
    const after = await api(page, 'GET', '/api/projects/1/statuses')
    expect((await after.json()).data.some((s) => s.name === statusName)).toBeFalsy()
  })

  test('删除有工作项占用的状态被拒绝', async ({ page }) => {
    const resp = await api(page, 'GET', '/api/projects/1/statuses')
    const list = (await resp.json()).data
    const inProgress = list.find((s) => s.type === 'STORY' && s.code === 'IN_PROGRESS')
    const del = await api(page, 'DELETE', `/api/projects/1/statuses/${inProgress.id}`)
    const body = await del.json()
    expect(body.code).toBe(400)
    expect(body.msg).toContain('不可删除')
  })

  test('初始状态不可删除', async ({ page }) => {
    const resp = await api(page, 'GET', '/api/projects/1/statuses')
    const list = (await resp.json()).data
    const initial = list.find((s) => s.type === 'STORY' && s.isInitial === 1)
    const del = await api(page, 'DELETE', `/api/projects/1/statuses/${initial.id}`)
    const body = await del.json()
    expect(body.code).toBe(400)
    expect(body.msg).toContain('初始状态不可删除')
  })
})
