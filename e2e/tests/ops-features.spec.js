import { expect } from '@playwright/test'
import { test } from '@playwright/test'
import { loginAs, submitDialog, api, uniqueName } from './helpers'

test.describe('DevOps 扩展（OPS）', () => {
  test('工作台 / 效能度量 页可访问', async ({ page }) => {
    await loginAs(page)
    await page.goto('/workbench')
    await expect(page.getByTestId('wb-runs')).toBeVisible()
    await expect(page.getByTestId('wb-deploys')).toBeVisible()

    await page.goto('/metrics')
    await expect(page.getByTestId('metrics-trend')).toBeVisible()
    await expect(page.locator('.sc-value').first()).toBeVisible()
  })

  test('顶栏通知铃铛可打开并全部已读', async ({ page }) => {
    await loginAs(page)
    await page.goto('/dashboard')
    await page.getByTestId('notif-bell').click()
    await expect(page.getByTestId('notif-dialog')).toBeVisible()
    await expect(page.locator('.notif-item').first()).toBeVisible()
    await submitDialog(page, 'notif-readall')
    await expect.poll(async () => (await page.locator('.notif-item.unread').count()) === 0).toBeTruthy()
  })

  test('个人令牌：创建后只展示一次明文', async ({ page }) => {
    await loginAs(page)
    await page.goto('/tokens')
    await submitDialog(page, 'token-create')
    await page.getByTestId('token-name').fill(uniqueName('e2e'))
    await page.locator('.el-dialog__footer button.el-button--primary').click({ force: true })
    await expect(page.getByTestId('token-plain')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByTestId('token-plain')).toHaveValue(/dfpat_/)
  })

  test('制品库可晋级 SNAPSHOT -> RELEASE', async ({ page }) => {
    await loginAs(page)
    await page.goto('/artifacts')
    const promoteBtn = page.locator('[data-testid=artifact-promote]').first()
    if (await promoteBtn.count()) {
      await promoteBtn.click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
      await expect(page.locator('.el-message').filter({ hasText: '已晋级' }).first()).toBeVisible({ timeout: 10_000 })
    }
  })

  test('部署到生产环境进入待审批并可审批通过', async ({ page }) => {
    await loginAs(page)
    const apps = (await (await api(page, 'GET', '/api/applications')).json()).data
    const gw = apps.find((a) => a.key === 'GATEWAY')
    const arts = (await (await api(page, 'GET', `/api/artifacts?applicationId=${gw.id}&size=20`)).json()).data.records
    const sn = arts.find((a) => a.stage !== 'RELEASE') || arts[0]
    const envs = (await (await api(page, 'GET', '/api/environments')).json()).data
    const prod = envs.find((e) => e.key === 'PROD')
    const dep = (await (await api(page, 'POST', '/api/deployments', {
      data: { applicationId: gw.id, artifactId: sn.id, environmentId: prod.id },
    })).json()).data
    expect(dep.status).toBe('PENDING_APPROVAL')

    await page.goto('/deployments')
    const approveBtn = page.locator(`[data-testid=deploy-approve-${dep.id}]`)
    await expect(approveBtn).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('.el-loading-mask')).toHaveCount(0)
    await approveBtn.click()
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await expect(approveBtn).toBeHidden({ timeout: 10_000 })
  })
})
