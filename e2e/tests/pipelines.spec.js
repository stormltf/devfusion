import { expect, test } from '@playwright/test'
import { loginAs, expectToast, uniqueName, api, pickOption } from './helpers'

test.describe.serial('应用与流水线', () => {
  let appName, appId, pipelineId

  test.beforeEach(async ({ page }) => {
    await loginAs(page)
  })

  test('新建应用', async ({ page }) => {
    appName = uniqueName('order-service')
    const appKey = appName.replace(/-/g, '').slice(0, 10).toUpperCase() + Math.floor(Math.random() * 900 + 100)
    await page.goto('/applications')
    await page.getByTestId('app-create').click()
    await page.getByTestId('app-name').fill(appName)
    await page.getByTestId('app-key').fill(appKey)
    await page.getByTestId('app-save').click()
    await expectToast(page, '创建成功')
    await expect(page.locator('.app-card', { hasText: appName })).toBeVisible()

    const resp = await api(page, 'GET', '/api/applications')
    appId = (await resp.json()).data.find((a) => a.name === appName).id
  })

  test('创建流水线并进入编排器', async ({ page }) => {
    await page.goto(`/applications/${appId}`)
    await page.getByTestId('pipeline-create').click()
    await page.getByTestId('pipeline-name').fill('E2E 构建流水线')
    await page.getByTestId('pipeline-save').click()
    await expect(page).toHaveURL(/pipelines\/\d+/, { timeout: 10_000 })
    pipelineId = Number(page.url().match(/pipelines\/(\d+)/)[1])
  })

  test('编排两个阶段共三个任务并保存', async ({ page }) => {
    await page.goto(`/applications/${appId}/pipelines/${pipelineId}`)

    // 创建流水线时自带一个默认骨架阶段，先清空再编排
    while (await page.locator('.stage-box').count()) {
      await page.locator('.stage-box .stage-head button').first().click()
      await page.waitForTimeout(200)
    }
    await expect(page.locator('.stage-box')).toHaveCount(0)

    // 阶段一：编译 + 单测
    await page.getByTestId('stage-add').click()
    await page.getByTestId('stage-name-0').fill('构建')
    await page.getByTestId('job-add-0').click()
    await page.getByTestId('job-name-0-0').fill('编译')
    await pickOption(page, page.locator('.job-card').first().locator('.el-select'), 'Maven 构建')

    await page.getByTestId('job-add-0').click()
    await page.getByTestId('job-name-0-1').fill('单测')
    await pickOption(page, page.locator('.job-card').nth(1).locator('.el-select'), '单元测试')

    // 阶段二：镜像
    await page.getByTestId('stage-add').click()
    await page.getByTestId('stage-name-1').fill('发布')
    await page.getByTestId('job-add-1').click()
    await page.getByTestId('job-name-1-0').fill('构建镜像')
    await pickOption(page, page.locator('.job-card').nth(2).locator('.el-select'), 'Docker 构建')

    await page.getByTestId('pipeline-save-editor').click()
    // 以持久化的编排结构为准
    await expect.poll(async () => {
      const d = await api(page, 'GET', `/api/pipelines/${pipelineId}`)
      const stages = (await d.json()).data.stages
      return `${stages.length}-${stages[0]?.jobs?.length ?? 0}-${stages[1]?.jobs?.length ?? 0}`
    }, { timeout: 15_000 }).toBe('2-2-1')
  })

  test('手动触发构建成功，日志与制品齐全', async ({ page }) => {
    await page.goto(`/applications/${appId}`)
    await page.locator('tr', { hasText: 'E2E 构建流水线' }).getByRole('button', { name: '触发构建' }).click()
    await expect(page).toHaveURL(/\/runs\/\d+/)

    // 等待构建终态成功（引擎 ~6-10s，前端 2s 轮询）
    await expect(page.getByTestId('run-status')).toHaveText('成功', { timeout: 60_000 })

    // 阶段任务全部成功
    await expect(page.getByTestId('run-stage-0')).toContainText('成功')
    await expect(page.getByTestId('run-stage-1')).toContainText('成功')

    // 展开日志查看内容
    await page.getByTestId('run-job-0-0').click()
    await expect(page.getByTestId('run-log-0-0')).toContainText('BUILD SUCCESS')

    // 版本号格式 {APPKEY}-{date}-{runId}
    const version = await page.getByTestId('run-version').textContent()
    expect(version).toMatch(/^[A-Z0-9]+-\d{12}-\d+$/)

    // 制品已生成
    const artifacts = await api(page, 'GET', `/api/artifacts?applicationId=${appId}`)
    expect((await artifacts.json()).data.total).toBe(1)
  })

  test('构建失败场景：单测失败阻断流水线', async ({ page }) => {
    // 把单测配置为失败
    const detail = await api(page, 'GET', `/api/pipelines/${pipelineId}`)
    const stages = (await detail.json()).data.stages
    const payload = {
      triggerType: 'MANUAL',
      stages: stages.map((s) => ({
        name: s.name,
        jobs: s.jobs.map((j) =>
          j.type === 'UNIT_TEST' ? { ...j, config: JSON.stringify({ testCases: 30, fail: true }) } : j,
        ),
      })),
    }
    await api(page, 'PUT', `/api/pipelines/${pipelineId}`, { data: payload })

    const run = await api(page, 'POST', `/api/pipelines/${pipelineId}/run`)
    const runId = (await run.json()).data.id

    // 轮询到终态
    let status = 'PENDING'
    for (let i = 0; i < 40 && !['SUCCESS', 'FAILED', 'CANCELED'].includes(status); i++) {
      await page.waitForTimeout(1000)
      const d = await api(page, 'GET', `/api/runs/${runId}`)
      status = (await d.json()).data.status
    }
    expect(status).toBe('FAILED')

    // 单测 FAILED，镜像任务 SKIPPED
    const d = await api(page, 'GET', `/api/runs/${runId}`)
    const rd = (await d.json()).data
    const unitJob = rd.stages[0].jobs.find((j) => j.type === 'UNIT_TEST')
    const dockerJob = rd.stages[1].jobs[0]
    expect(unitJob.status).toBe('FAILED')
    expect(dockerJob.status).toBe('SKIPPED')
    expect(unitJob.log).toContain('Failures: 3')
  })

  test('运行中取消构建', async ({ page }) => {
    // 先恢复干净的单测配置（上一个失败场景把它改成了必失败）
    const detail = await api(page, 'GET', `/api/pipelines/${pipelineId}`)
    const clean = (await detail.json()).data.stages
    await api(page, 'PUT', `/api/pipelines/${pipelineId}`, {
      data: {
        stages: clean.map((s) => ({
          name: s.name,
          jobs: s.jobs.map((j) =>
            j.type === 'UNIT_TEST' ? { ...j, config: JSON.stringify({ testCases: 30 }) } : j),
        })),
      },
    })

    const run = await api(page, 'POST', `/api/pipelines/${pipelineId}/run`)
    const runId = (await run.json()).data.id
    await page.waitForTimeout(1500)
    await api(page, 'POST', `/api/runs/${runId}/cancel`)

    let status = 'RUNNING'
    for (let i = 0; i < 30 && !['SUCCESS', 'FAILED', 'CANCELED'].includes(status); i++) {
      await page.waitForTimeout(1000)
      const d = await api(page, 'GET', `/api/runs/${runId}`)
      status = (await d.json()).data.status
    }
    expect(status).toBe('CANCELED')
  })

  test('Webhook 触发', async ({ page }) => {
    // 先把流水线改为 WEBHOOK 触发
    const detail = await api(page, 'GET', `/api/pipelines/${pipelineId}`)
    const p = (await detail.json()).data
    const stages = p.stages
    await api(page, 'PUT', `/api/pipelines/${pipelineId}`, {
      data: { triggerType: 'WEBHOOK', stages: stages.map((s) => ({ name: s.name, jobs: s.jobs })) },
    })

    // 无 token 403
    const bad = await api(page, 'POST', `/api/webhook/pipelines/${pipelineId}/trigger?token=wrong`)
    expect((await bad.json()).code).toBe(403)

    // 正确 token 触发成功
    const token = p.webhookToken
    const resp = await api(page, 'POST', `/api/webhook/pipelines/${pipelineId}/trigger?token=${token}`)
    const body = await resp.json()
    expect(body.code).toBe(0)
    expect(body.data.runId).toBeGreaterThan(0)
    expect(body.data.version).toBeTruthy()
    // 等它跑完
    let status = 'PENDING'
    for (let i = 0; i < 40 && !['SUCCESS', 'FAILED', 'CANCELED'].includes(status); i++) {
      await page.waitForTimeout(1000)
      const d = await api(page, 'GET', `/api/runs/${body.data.runId}`)
      status = (await d.json()).data.status
    }
    expect(status).toBe('SUCCESS')
  })

  test('停用流水线后触发被拒绝', async ({ page }) => {
    const detail = await api(page, 'GET', `/api/pipelines/${pipelineId}`)
    const p = (await detail.json()).data
    await api(page, 'PUT', `/api/pipelines/${pipelineId}`, {
      data: { status: 'DISABLED', stages: p.stages.map((s) => ({ name: s.name, jobs: s.jobs })) },
    })
    const resp = await api(page, 'POST', `/api/pipelines/${pipelineId}/run`)
    expect((await resp.json()).msg).toContain('停用')
  })

  test('删除流水线', async ({ page }) => {
    const del = await api(page, 'DELETE', `/api/pipelines/${pipelineId}`)
    expect((await del.json()).code).toBe(0)
    const detail = await api(page, 'GET', `/api/pipelines/${pipelineId}`)
    expect((await detail.json()).code).toBe(404)
  })
})
