import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('环境与部署', () => {
  let envId, appId, artifactId

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('环境管理：新建/编辑/删除', async ({ page }) => {
    const envKey = 'E2E' + Math.floor(Math.random() * 9000 + 1000)
    await page.goto('/environments')
    await page.getByTestId('env-create').click()
    await page.getByTestId('env-name').fill('E2E 专用环境')
    await page.getByTestId('env-key').fill(envKey)
    await page.getByTestId('env-save').click()
    await expectToast(page, '已保存')
    await expect(page.locator('.env-card', { hasText: 'E2E 专用环境' })).toBeVisible()

    const envs = await api(page, 'GET', '/api/environments')
    envId = (await envs.json()).data.find((e) => e.key === envKey).id

    // 编辑
    await page.getByTestId(`env-edit-${envId}`).click()
    await page.getByTestId('env-name').fill('E2E 专用环境（改）')
    await page.getByTestId('env-save').click()
    await expectToast(page, '已保存')
    await expect(page.locator('.env-card', { hasText: 'E2E 专用环境（改）' })).toBeVisible()

    // 删除
    await page.getByTestId(`env-delete-${envId}`).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expectToast(page, '已删除')
  })

  test('发起部署单并自动执行成功', async ({ page }) => {
    // 用种子应用 GATEWAY + 其最新制品
    const apps = await api(page, 'GET', '/api/applications')
    appId = (await apps.json()).data.find((a) => a.key === 'GATEWAY').id
    const artifacts = await api(page, 'GET', `/api/artifacts?applicationId=${appId}&size=1`)
    artifactId = (await artifacts.json()).data.records[0].id

    const envs = await api(page, 'GET', '/api/environments')
    const testEnv = (await envs.json()).data.find((e) => e.key === 'TEST')

    await page.goto('/deployments')
    await page.getByTestId('deploy-create').click()
    await pickOption(page, page.getByTestId('deploy-app-select'), 'gateway-service')
    const latest = (await (await api(page, 'GET', `/api/artifacts?applicationId=${appId}&size=1`)).json()).data.records[0].version
    await pickOption(page, page.getByTestId('deploy-artifact-select'), latest)
    await pickOption(page, page.getByTestId('deploy-env-select'), '测试环境')
    await page.getByTestId('deploy-save').click()
    await expectToast(page, '部署单已发起')

    // 轮询第一行（刚发起的部署单）状态到成功
    const firstRow = page.getByTestId('deploy-table').locator('tbody tr').first()
    await expect(firstRow.locator('[data-testid^="deploy-status-"]')).toHaveText(/成功/, { timeout: 60_000 })

    // 部署日志有四步内容
    await firstRow.getByRole('button', { name: '日志' }).click()
    await expect(page.getByTestId('deploy-log-content')).toContainText('健康检查')
    await expect(page.getByTestId('deploy-log-content')).toContainText('部署成功')
  })

  test('部署单回滚到上一个成功版本', async ({ page }) => {
    // 依次部署两个不同版本到 TEST 环境，再回滚最后一个
    const envs = (await (await api(page, 'GET', '/api/environments')).json()).data
    const testEnvId = envs.find((e) => e.key === 'TEST').id
    const artifacts = (await (await api(page, 'GET', `/api/artifacts?applicationId=${appId}&size=50`)).json()).data.records
    const sorted = [...artifacts].sort((a, b) => a.id - b.id)
    expect(sorted.length).toBeGreaterThanOrEqual(2)

    for (const art of sorted.slice(-2)) {
      const dep = await api(page, 'POST', '/api/deployments', {
        data: { applicationId: appId, artifactId: art.id, environmentId: testEnvId },
      })
      const depId = (await dep.json()).data.id
      await waitDeployDone(page, depId)
    }
    const dep = await api(page, 'POST', '/api/deployments', {
      data: { applicationId: appId, artifactId: sorted[sorted.length - 1].id, environmentId: testEnvId },
    })
    const depId = (await dep.json()).data.id
    await waitDeployDone(page, depId)

    const rollback = await api(page, 'POST', `/api/deployments/${depId}/rollback`)
    const body = await rollback.json()
    expect(body.code, JSON.stringify(body.msg) + ' depId=' + depId).toBe(0)
    expect(body.data.deployType).toBe('ROLLBACK')

    // 回滚单执行成功
    await waitDeployDone(page, body.data.id)
    const detail = await api(page, 'GET', `/api/deployments/${body.data.id}`)
    expect((await detail.json()).data.status).toBe('SUCCESS')
  })

  test('回滚无历史版本时报错', async ({ page }) => {
    // 新应用：建最小流水线并真实构建出第一个制品，部署成功后无更早版本可回滚
    const suffix = Date.now().toString(36)
    const app = await api(page, 'POST', '/api/applications', {
      data: { name: `rollback-alone-${suffix}`, key: `RBONE${suffix.toUpperCase().slice(-6)}`, language: 'JAVA' },
    })
    const newAppId = (await app.json()).data.id
    const pipe = await api(page, 'POST', `/api/applications/${newAppId}/pipelines`, {
      data: {
        name: 'rb-build',
        stages: [{ name: 'build', jobs: [{ name: 'echo', type: 'SHELL', config: '{"script":"echo hi"}' }] }],
      },
    })
    const pipeId = (await pipe.json()).data.id
    const run = await api(page, 'POST', `/api/pipelines/${pipeId}/run`)
    await waitRunDone(page, (await run.json()).data.id)

    const artifacts = await api(page, 'GET', `/api/artifacts?applicationId=${newAppId}`)
    const artifact = (await artifacts.json()).data.records[0]
    expect(artifact).toBeTruthy()

    const dep = await api(page, 'POST', '/api/deployments', {
      data: {
        applicationId: newAppId,
        artifactId: artifact.id,
        environmentId: (await (await api(page, 'GET', '/api/environments')).json()).data.find((e) => e.key === 'DEV').id,
      },
    })
    const depId = (await dep.json()).data.id
    await waitDeployDone(page, depId)

    const rollback = await api(page, 'POST', `/api/deployments/${depId}/rollback`)
    const body = await rollback.json()
    expect(body.code).toBe(400)
    expect(body.msg).toContain('没有更早的成功版本')
  })

  test('部署失败场景：制品不存在', async ({ page }) => {
    const dep = await api(page, 'POST', '/api/deployments', {
      data: {
        applicationId: appId,
        artifactId: 999999,
        environmentId: (await (await api(page, 'GET', '/api/environments')).json()).data.find((e) => e.key === 'DEV').id,
      },
    })
    const body = await dep.json()
    expect(body.code).toBe(400)
    expect(body.msg).toContain('制品不存在')
  })

  test('取消进行中的部署', async ({ page }) => {
    const dep = await api(page, 'POST', '/api/deployments', {
      data: {
        applicationId: appId,
        artifactId,
        environmentId: (await (await api(page, 'GET', '/api/environments')).json()).data.find((e) => e.key === 'DEV').id,
      },
    })
    const depId = (await dep.json()).data.id
    await page.waitForTimeout(1200)
    const cancel = await api(page, 'POST', `/api/deployments/${depId}/cancel`)
    expect((await cancel.json()).code).toBe(0)

    let status = 'DEPLOYING'
    for (let i = 0; i < 30 && !['SUCCESS', 'FAILED', 'CANCELED'].includes(status); i++) {
      await page.waitForTimeout(1000)
      status = (await (await api(page, 'GET', `/api/deployments/${depId}`)).json()).data.status
    }
    expect(status).toBe('CANCELED')
  })
})

async function waitDeployDone(page, depId) {
  let status = 'PENDING'
  for (let i = 0; i < 40 && !['SUCCESS', 'FAILED', 'CANCELED'].includes(status); i++) {
    await page.waitForTimeout(1000)
    status = (await (await api(page, 'GET', `/api/deployments/${depId}`)).json()).data.status
  }
  expect(['SUCCESS', 'FAILED', 'CANCELED']).toContain(status)
  return status
}

async function waitRunDone(page, runId) {
  let status = 'PENDING'
  for (let i = 0; i < 60 && !['SUCCESS', 'FAILED', 'CANCELED'].includes(status); i++) {
    await page.waitForTimeout(1000)
    status = (await (await api(page, 'GET', `/api/runs/${runId}`)).json()).data.status
  }
  expect(['SUCCESS', 'FAILED', 'CANCELED']).toContain(status)
  return status
}
