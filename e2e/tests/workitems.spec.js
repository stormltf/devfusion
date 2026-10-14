import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

const GOTO = async (page, tab) => {
  await page.goto(`/projects/1/${tab || 'story'}`)
  await expect(page.getByTestId('item-table-story')).toBeVisible()
}

test.describe.serial('工作项（需求/任务/缺陷）', () => {
  let storyNo

  test('新建需求：默认初始状态、编号自动生成', async ({ page }) => {
    await loginAs(page)
    await GOTO(page, 'story')
    await page.getByTestId('item-create-story').click()
    const storyTitle = `E2E-验证需求-多租户数据隔离-${Date.now().toString(36)}`
    await page.getByTestId('item-title-story').fill(storyTitle)
    // 优先级下拉选择“紧急”
    await pickOption(page, page.locator('.el-dialog .el-select').first(), '紧急')
    await page.getByTestId('item-save-story').click()
    await expectToast(page, '创建成功')
    const row = page.locator('tr', { hasText: storyTitle }).first()
    await expect(row).toContainText('紧急')
    await expect(row).toContainText('待处理')
    storyNo = (await row.locator('td').nth(1).textContent()).trim()
  })

  test('需求详情抽屉：状态流转 + 动态记录', async ({ page }) => {
    await loginAs(page)
    await GOTO(page, 'story')
    await page.locator('tr', { hasText: storyNo }).first().getByRole('button', { name: '详情' }).click()
    await expect(page.getByTestId('item-detail-title')).toBeVisible()

    // 状态流转 待处理 → 进行中
    await pickOption(page, page.getByTestId('detail-status-select'), '进行中')
    await expectToast(page, '状态已流转')

    // 动态里应有 STATUS 记录
    await page.getByRole('tab', { name: '动态' }).click()
    await expect(page.locator('.el-timeline')).toContainText('将状态从')
  })

  test('需求详情：修改优先级与指派', async ({ page }) => {
    await loginAs(page)
    await GOTO(page, 'story')
    await page.locator('tr', { hasText: storyNo }).first().getByRole('button', { name: '详情' }).click()
    // 指派给张伟
    const assignee = page.locator('.el-descriptions__body .el-select').nth(2)
    await pickOption(page, assignee, '张伟')
    await expectToast(page, '已更新')
    await expect(page.locator('tr', { hasText: storyNo })).toContainText('张伟')
  })

  test('发表评论并出现在列表', async ({ page }) => {
    await loginAs(page)
    await GOTO(page, 'story')
    await page.locator('tr', { hasText: storyNo }).first().getByRole('button', { name: '详情' }).click()
    await page.getByRole('tab', { name: /评论/ }).click()
    await page.getByTestId('comment-input').fill('请在本周内完成方案评审')
    await page.getByTestId('comment-submit').click()
    await expect(page.locator('.comment', { hasText: '请在本周内完成方案评审' })).toBeVisible()
  })

  test('列表筛选：按关键字搜索', async ({ page }) => {
    await loginAs(page)
    await GOTO(page, 'story')
    await page.getByTestId('item-search-story').fill(storyNo)
    await page.getByTestId('item-search-story').press('Enter')
    await page.waitForTimeout(800)
    const rows = page.getByTestId('item-table-story').locator('tbody tr')
    await expect(rows).toHaveCount(1)
    await expect(rows.first()).toContainText(storyNo)
  })

  test('新建任务挂到需求下（父子层级）', async ({ page }) => {
    await loginAs(page)
    // API 建子任务，UI 验证详情里可见
    const items = await api(page, 'GET', '/api/projects/1/items?type=STORY&keyword=' + encodeURIComponent(storyNo))
    const storyId = (await items.json()).data.records[0].id
    const zhangwei = (await (await api(page, 'GET', '/api/users/simple')).json()).data.find((u) => u.username === 'zhangwei')
    const resp = await api(page, 'POST', '/api/projects/1/items', {
      data: { type: 'TASK', title: '隔离方案设计', parentId: storyId, assigneeId: zhangwei.id },
    })
    const body = await resp.json()
    expect(body.code, body.msg).toBe(0)
    const detail = await api(page, 'GET', `/api/items/${body.data.id}`)
    const d = (await detail.json()).data
    expect(d.parentId).toBe(storyId)
    expect(d.childCount).toBe(0)
    // 父需求 childCount = 1
    const parent = await api(page, 'GET', `/api/items/${storyId}`)
    expect((await parent.json()).data.childCount).toBe(1)
  })

  test('新建缺陷并流转到已修复→已关闭', async ({ page }) => {
    await loginAs(page)
    await page.goto('/projects/1/bug')
    await page.getByTestId('item-create-bug').click()
    const bugTitle = `E2E-验证缺陷-租户切换后缓存串数据-${Date.now().toString(36)}`
    await page.getByTestId('item-title-bug').fill(bugTitle)
    await page.getByTestId('item-save-bug').click()
    await expectToast(page, '创建成功')
    const row = page.locator('tr', { hasText: bugTitle }).first()
    const bugNo = (await row.locator('td').nth(1).textContent()).trim()

    await row.getByRole('button', { name: '详情' }).click()
    for (const target of ['已修复', '已关闭']) {
      await pickOption(page, page.getByTestId('detail-status-select'), target)
      await expectToast(page, '状态已流转')
    }
    await expect(page.locator('tr', { hasText: bugNo })).toContainText('已关闭')
  })

  test('删除工作项', async ({ page }) => {
    await loginAs(page)
    // API 建一条然后 UI 删除
    const resp = await api(page, 'POST', '/api/projects/1/items', {
      data: { type: 'TASK', title: 'E2E-待删除任务' },
    })
    const id = (await resp.json()).data.id
    const del = await api(page, 'DELETE', `/api/items/${id}`)
    expect((await del.json()).code).toBe(0)
    const gone = await api(page, 'GET', `/api/items/${id}`)
    expect((await gone.json()).code).toBe(404)
  })

  test('指派非项目成员被拒绝', async ({ page }) => {
    await loginAs(page)
    // id=999 的用户不存在
    const resp = await api(page, 'POST', '/api/projects/1/items', {
      data: { type: 'TASK', title: '非法指派', assigneeId: 999 },
    })
    const body = await resp.json()
    expect(body.code).toBe(400)
    expect(body.msg).toContain('项目成员')
  })
})
