import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api } from './helpers'

test.describe.serial('迭代管理', () => {
  let iterName, iterId

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('新建迭代', async ({ page }) => {
    iterName = uniqueName('迭代E2E')
    await page.goto('/projects/1/iteration')
    await page.getByTestId('iteration-create').click()
    await page.getByTestId('iteration-name').fill(iterName)
    await page.getByTestId('iteration-save').click()
    await expectToast(page, '创建成功')
    const row = page.locator('tr', { hasText: iterName })
    await expect(row).toContainText('规划中')
    await expect(row).toContainText('0%')
  })

  test('启动迭代 → 规划中变进行中', async ({ page }) => {
    await page.goto('/projects/1/iteration')
    iterId = await getIdFromRow(page, iterName)
    await page.getByTestId(`iteration-start-${iterId}`).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '迭代已进行中')
    await expect(page.locator('tr', { hasText: iterName })).toContainText('进行中')
  })

  test('有未完成工作项时不能完成迭代（状态机校验）', async ({ page }) => {
    // 往迭代里塞一个未完成任务
    const resp = await api(page, 'POST', '/api/projects/1/items', {
      data: { type: 'TASK', title: 'E2E-迭代内未完成任务', iterationId: iterId },
    })
    expect((await resp.json()).code).toBe(0)

    await page.goto('/projects/1/iteration')
    await page.getByTestId(`iteration-complete-${iterId}`).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '不能完成迭代')
    await expect(page.locator('tr', { hasText: iterName })).toContainText('进行中')
  })

  test('完成全部工作项后可以完成迭代', async ({ page }) => {
    // 把迭代内工作项全部流转到 DONE
    const resp = await api(page, 'GET', `/api/iterations/${iterId}`)
    const { items } = (await resp.json()).data
    // 各类型工作流不同，按 item 类型取 category=DONE 的第一个状态码
    const statuses = (await (await api(page, 'GET', '/api/projects/1/statuses')).json()).data
    for (const item of items) {
      const doneCode = statuses.find((st) => st.type === item.type && st.category === 'DONE').code
      await api(page, 'POST', `/api/items/${item.id}/transition`, { data: { statusCode: doneCode } })
    }
    await page.goto('/projects/1/iteration')
    await page.getByTestId(`iteration-complete-${iterId}`).click()
    await page.getByRole('button', { name: '确定' }).click()
    // 以持久化状态为准（toast 时序不稳定）
    await expect(page.locator('tr', { hasText: iterName })).toContainText('已完成', { timeout: 15_000 })
    await expect(page.locator('tr', { hasText: iterName })).toContainText('100%')
  })

  test('种子迭代「迭代二」处于规划中', async ({ page }) => {
    await page.goto('/projects/1/iteration')
    await expect(page.locator('tr', { hasText: '迭代二：灰度发布' })).toContainText('规划中')
  })

  test('已完成迭代不允许再次启动（非法流转被拒）', async ({ page }) => {
    const resp = await api(page, 'PUT', `/api/iterations/${iterId}/status`, {
      data: { status: 'IN_PROGRESS' },
    })
    const body = await resp.json()
    expect(body.code).toBe(400)
    expect(body.msg).toContain('不允许')
  })

  test('删除空迭代', async ({ page }) => {
    // 先清空迭代内工作项（前面流转到 DONE 的）
    const itemsResp = await api(page, 'GET', `/api/iterations/${iterId}`)
    for (const item of (await itemsResp.json()).data.items) {
      await api(page, 'DELETE', `/api/items/${item.id}`)
    }
    await page.goto('/projects/1/iteration')
    await page.getByTestId(`iteration-delete-${iterId}`).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '已删除')
    await expect(page.locator('tr', { hasText: iterName })).toHaveCount(0)
  })
})

async function getIdFromRow(page, name) {
  const items = await api(page, 'GET', '/api/projects/1/iterations')
  const list = (await items.json()).data
  return list.find((i) => i.name === name).id
}
