import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe('总览与制品库', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('总览统计卡片与图表渲染', async ({ page }) => {
    await page.goto('/dashboard')
    // 四张统计卡的数值为数字
    await expect(page.locator('.stat-card', { hasText: '项目数' }).locator('.sc-value')).toHaveText(/^\d+$/)
    await expect(page.locator('.stat-card', { hasText: '应用数' }).locator('.sc-value')).toHaveText(/^\d+$/)
    await expect(page.locator('.stat-card', { hasText: '构建次数' }).locator('.sc-value')).toHaveText(/^\d+$/)
    await expect(page.locator('.stat-card', { hasText: '部署次数' }).locator('.sc-value')).toHaveText(/^\d+$/)
    // ECharts 画布渲染
    await expect(page.locator('canvas').first()).toBeVisible()
    // 最近部署表格有种子数据
    await expect(page.locator('.el-table')).toContainText('GATEWAY')
  })

  test('制品库列表展示构建产物', async ({ page }) => {
    await page.goto('/artifacts')
    await expect(page.getByTestId('artifact-table')).toContainText('GATEWAY')
    await expect(page.getByTestId('artifact-table')).toContainText('registry.devfusion.local')
  })

  test('制品库按应用过滤', async ({ page }) => {
    await page.goto('/artifacts')
    await pickOption(page, page.locator('.el-select').first(), 'gateway-console')
    await expect(page.getByTestId('artifact-table')).toContainText('CONSOLE-')
    // 过滤后不应再出现其他应用的制品
    await expect(page.getByTestId('artifact-table')).not.toContainText('GATEWAY-')
  })
})
