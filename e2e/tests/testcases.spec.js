import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('测试用例', () => {
  let caseTitle

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('新建用例', async ({ page }) => {
    caseTitle = uniqueName('E2E-用例-限流阈值生效')
    await page.goto('/projects/1/testcase')
    await page.getByTestId('case-create').click()
    await page.getByTestId('case-title').fill(caseTitle)
    await page.locator('.el-dialog textarea').nth(1).fill('以 200 QPS 压测阈值 100 的路由')
    await page.getByTestId('case-save').click()
    await expectToast(page, '已保存')
    const row = page.locator('tr', { hasText: caseTitle })
    await expect(row).toContainText('未执行')
  })

  test('执行标记通过，统计联动', async ({ page }) => {
    await page.goto('/projects/1/testcase')
    const row = page.locator('tr', { hasText: caseTitle })
    const caseId = await row.getAttribute('data-id').catch(() => null)
    await row.getByRole('button', { name: '通过' }).click()
    await expectToast(page, '已标记 通过')
    await expect(page.locator('tr', { hasText: caseTitle })).toContainText('通过')
    // 统计卡：通过数 +1（种子 1 个 PASS + 本用例 = 2）
    const stats = await api(page, 'GET', '/api/projects/1/test-stats')
    expect((await stats.json()).data.PASS).toBeGreaterThanOrEqual(2)
  })

  test('执行标记失败可关联缺陷', async ({ page }) => {
    // 先建一个缺陷工作项
    const bug = await api(page, 'POST', '/api/projects/1/items', {
      data: { type: 'BUG', title: 'E2E-用例失败关联缺陷' },
    })
    const bugId = (await bug.json()).data.id

    await page.goto('/projects/1/testcase')
    const row = page.locator('tr', { hasText: caseTitle })
    await row.getByRole('button', { name: '失败' }).click()
    await page.locator('.el-message-box__input input').fill(String(bugId))
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '已标记 失败')

    // 后端已记录关联
    const cases = await api(page, 'GET', '/api/projects/1/test-cases?keyword=' + encodeURIComponent(caseTitle))
    const target = (await cases.json()).data.records[0]
    expect(target.latestResult).toBe('FAIL')
    expect(target.bugItemId).toBe(bugId)
  })

  test('执行标记阻塞', async ({ page }) => {
    await page.goto('/projects/1/testcase')
    await page.locator('tr', { hasText: caseTitle }).getByRole('button', { name: '阻塞' }).click()
    await expectToast(page, '已标记 阻塞')
    await expect(page.locator('tr', { hasText: caseTitle })).toContainText('阻塞')
  })

  test('编辑用例', async ({ page }) => {
    await page.goto('/projects/1/testcase')
    await page.locator('tr', { hasText: caseTitle }).getByRole('button', { name: '编辑' }).click()
    await page.getByTestId('case-title').fill(caseTitle + '（改）')
    await page.getByTestId('case-save').click()
    await expectToast(page, '已保存')
    await expect(page.getByTestId('case-table')).toContainText(caseTitle + '（改）')
  })

  test('删除用例', async ({ page }) => {
    await page.goto('/projects/1/testcase')
    await page.locator('tr', { hasText: caseTitle }).getByRole('button', { name: '删除' }).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '已删除')
    await expect(page.getByTestId('case-table')).not.toContainText(caseTitle)
  })

  test('种子用例执行结果统计正确', async ({ page }) => {
    const stats = await api(page, 'GET', '/api/projects/1/test-stats')
    const d = (await stats.json()).data
    expect(d.total).toBeGreaterThanOrEqual(3)
    expect(d.UNEXECUTED).toBeGreaterThanOrEqual(1)
  })
})
