import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('看板', () => {
  let cardId

  test('看板按类型渲染列与卡片', async ({ page }) => {
    await loginAs(page)
    await page.goto('/projects/1/board')
    await expect(page.getByTestId('board-col-TODO')).toBeVisible()
    await expect(page.getByTestId('board-col-IN_PROGRESS')).toBeVisible()
    await expect(page.getByTestId('board-col-DONE')).toBeVisible()
    // 种子数据里有需求
    await expect(page.locator('.board-card')).not.toHaveCount(0)
  })

  test('切换到缺陷类型看板显示 BUG 状态列', async ({ page }) => {
    await loginAs(page)
    await page.goto('/projects/1/board')
    // el-radio-button 的原生 input 视觉隐藏，点外层 label
    await page.locator('.el-radio-button', { hasText: '缺陷' }).click()
    await expect(page.getByTestId('board-col-NEW')).toBeVisible()
    await expect(page.getByTestId('board-col-CLOSED')).toBeVisible()
  })

  test('拖拽卡片流转状态（HTML5 drag & drop）', async ({ page }) => {
    await loginAs(page)
    // 建一张测试需求放在初始列
    const resp = await api(page, 'POST', '/api/projects/1/items', {
      data: { type: 'STORY', title: 'E2E-看板拖拽卡' },
    })
    cardId = (await resp.json()).data.id

    await page.goto('/projects/1/board')
    const card = page.getByTestId(`board-card-${cardId}`)
    await expect(card).toBeVisible()

    // Playwright HTML5 拖拽：dragstart → drop
    const target = page.getByTestId('board-col-IN_PROGRESS')
    await card.dragTo(target)
    await expectToast(page, '→ 进行中')

    // 卡片出现在新列
    await expect(target.getByTestId(`board-card-${cardId}`)).toBeVisible()

    // 后端状态同步
    const detail = await api(page, 'GET', `/api/items/${cardId}`)
    expect((await detail.json()).data.statusCode).toBe('IN_PROGRESS')
  })

  test('拖拽到 DONE 列完成流转', async ({ page }) => {
    await loginAs(page)
    await page.goto('/projects/1/board')
    const card = page.getByTestId(`board-card-${cardId}`)
    await card.dragTo(page.getByTestId('board-col-DONE'))
    await expectToast(page, '→ 已完成')
    const detail = await api(page, 'GET', `/api/items/${cardId}`)
    expect((await detail.json()).data.statusCode).toBe('DONE')
  })
})
