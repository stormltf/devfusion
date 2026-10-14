import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api } from './helpers'

test.describe.serial('PM 项目管理新功能', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('Backlog 页展示待规划工作项', async ({ page }) => {
    const title = uniqueName('BacklogE2E')
    await api(page, 'POST', '/api/projects/1/items', { data: { type: 'STORY', title } })
    await page.goto('/projects/1/backlog')
    await expect(page.getByTestId('backlog-unplanned')).toBeVisible()
    await expect(page.locator('.backlog-layout', { hasText: title })).toBeVisible()
  })

  test('Backlog 规划/移出迭代（API 留痕，UI 持久化断言）', async ({ page }) => {
    const title = uniqueName('BacklogPlanE2E')
    const create = await (await api(page, 'POST', '/api/projects/1/items', { data: { type: 'STORY', title } })).json()
    const itemId = create.data.id
    // 规划进迭代 1
    await api(page, 'POST', '/api/projects/1/backlog/plan', { data: { itemIds: [itemId], iterationId: 1 } })
    await page.goto('/projects/1/backlog')
    await expect(page.getByTestId('backlog-iter-1').locator(`[data-testid="backlog-item-${itemId}"]`)).toBeVisible()
    // 移出
    await api(page, 'POST', '/api/projects/1/backlog/unplan', { data: { itemIds: [itemId] } })
    await page.goto('/projects/1/backlog')
    await expect(page.getByTestId('backlog-unplanned').locator(`[data-testid="backlog-item-${itemId}"]`)).toBeVisible()
  })

  test('发布管理：新建 → 发布流转', async ({ page }) => {
    const name = uniqueName('ReleaseE2E')
    await page.goto('/projects/1/release')
    await page.getByTestId('release-create').click()
    await page.getByTestId('release-name').fill(name)
    await page.getByTestId('release-version').fill('1.0.0')
    await page.getByTestId('release-save').click()
    await expectToast(page, '创建成功')
    const row = page.locator('tr', { hasText: name })
    await expect(row).toContainText('规划中')
    const list = await (await api(page, 'GET', '/api/projects/1/releases')).json()
    const rid = list.data.find((r) => r.name === name).id
    await page.goto('/projects/1/release')
    await page.getByTestId(`release-publish-${rid}`).click()
    await expect(page.locator('tr', { hasText: name })).toContainText('已发布', { timeout: 10_000 })
  })

  test('测试计划：新建 → 加入用例 → 执行通过', async ({ page }) => {
    const name = uniqueName('PlanE2E')
    await page.goto('/projects/1/testplan')
    await page.getByTestId('plan-create').click()
    await page.getByTestId('plan-name').fill(name)
    await page.getByTestId('plan-save').click()
    await expectToast(page, '创建成功')
    const plans = await (await api(page, 'GET', '/api/projects/1/test-plans')).json()
    const pid = plans.data.find((p) => p.name === name).id
    await page.getByTestId(`plan-detail-${pid}`).click()
    // 新建独立用例，避免改动种子用例的执行状态（test-stats 断言依赖种子“未执行”态）
    const created = await api(page, 'POST', '/api/projects/1/test-cases', {
      data: { title: uniqueName('PlanE2E-Case'), priority: 'MEDIUM' },
    })
    const caseId = (await created.json()).data.id
    await api(page, 'POST', `/api/test-plans/${pid}/cases`, { data: { caseIds: [caseId] } })
    await page.reload()
    await page.getByTestId(`plan-detail-${pid}`).click()
    await expect(page.getByTestId('plan-case-table').locator('tr', { hasText: String(caseId) }).first()).toBeVisible()
    await page.getByTestId(`plan-pass-${caseId}`).first().click()
    await expectToast(page, '已标记 通过')
  })

  test('迭代概览：阶段时间轴与燃尽图容器可见', async ({ page }) => {
    await page.goto('/projects/1/iteration')
    await page.getByTestId('iteration-overview-1').click()
    await expect(page.getByTestId('iteration-stages')).toBeVisible()
    await expect(page.locator('.ov-burndown-head')).toContainText('燃尽图')
  })

  test('缺陷报表页渲染统计卡片与图表', async ({ page }) => {
    await page.goto('/projects/1/report')
    await expect(page.locator('.stat-row')).toBeVisible()
    await expect(page.locator('.el-row').filter({ hasText: '未解决缺陷' })).toBeVisible()
  })

  test('工作项列表：排序选择可见', async ({ page }) => {
    await page.goto('/projects/1/story')
    await expect(page.getByTestId('sort-field')).toBeVisible()
    await expect(page.getByTestId('view-select')).toBeVisible()
  })
})
