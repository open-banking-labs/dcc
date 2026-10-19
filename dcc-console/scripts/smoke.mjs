// 真实数据冒烟：用前端同款接口把各页面依赖的数据路径跑一遍。
// 会往 dev 库写入数据（幂等：按 code 复用已存在的记录）。
//
//   node scripts/smoke.mjs
//
// 环境变量：DCC_API_BASE(默认 http://localhost:8080) / DCC_JWT_SECRET / DCC_TENANT
import crypto from 'node:crypto'

const BASE = process.env.DCC_API_BASE ?? 'http://localhost:8080'
const SECRET = process.env.DCC_JWT_SECRET ?? 'dev-only-change-me-please-use-32-bytes'
const TENANT = process.env.DCC_TENANT ?? 'default_tenant'

function mintToken() {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url')
  const now = Math.floor(Date.now() / 1000)
  const header = { alg: 'HS256', typ: 'JWT' }
  const payload = {
    sub: 'smoke',
    tenant: TENANT,
    roles: ['DCC_TENANT_ADMIN', 'DCC_MAINTAINER', 'DCC_APPROVER'],
    iat: now,
    exp: now + 3600,
  }
  const unsigned = `${b64(header)}.${b64(payload)}`
  const sig = crypto.createHmac('sha256', SECRET).update(unsigned).digest('base64url')
  return `${unsigned}.${sig}`
}

const TOKEN = mintToken()

let passed = 0
let failed = 0
function check(name, cond, detail) {
  if (cond) {
    passed++
    console.log(`  \u2713 ${name}`)
  } else {
    failed++
    console.log(`  \u2717 ${name}  ${detail ?? ''}`)
  }
}

async function call(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }
  return { status: res.status, ok: res.ok, data }
}

const listFind = (arr, key, value) => (Array.isArray(arr) ? arr.find((x) => x[key] === value) : undefined)

async function section(title) {
  console.log(`\n${title}`)
}

async function main() {
  await section('平台管理：环境 / 应用')
  const envList = (await call('GET', '/api/environments')).data ?? []
  let dev = listFind(envList, 'code', 'DEV')
  if (!dev) dev = (await call('POST', '/api/environments', { code: 'DEV', name: '开发', description: '开发环境', sortOrder: 0 })).data
  let test = listFind(envList, 'code', 'TEST')
  if (!test) test = (await call('POST', '/api/environments', { code: 'TEST', name: '测试', description: '测试环境', sortOrder: 1 })).data
  check('环境 DEV / TEST 就绪', dev?.id != null && test?.id != null, JSON.stringify({ dev, test }))

  const appList = (await call('GET', '/api/applications')).data ?? []
  let app = listFind(appList, 'code', 'CIF')
  if (!app) app = (await call('POST', '/api/applications', { code: 'CIF', name: '客户系统', description: 'CIF 应用' })).data
  check('应用 CIF 就绪', app?.id != null)

  const envId = dev.id
  const appId = app.id
  const scope = `environmentId=${envId}&applicationId=${appId}`

  await section('数标：创建 / 列表 / 详情 / 历史 / 校验 / 引用 / 流转')
  let stdList = (await call('GET', `/api/standards?${scope}`)).data ?? []
  let std = listFind(stdList, 'code', 'CUST_NAME')
  if (!std) {
    const res = await call('POST', `/api/standards?${scope}`, {
      code: 'CUST_NAME',
      category: 'CIF',
      content: {
        name: '客户名称',
        description: '客户中文名称',
        dataType: 'VARCHAR',
        length: 64,
        scale: null,
        required: true,
        defaultValue: null,
        enumValues: null,
        regex: null,
        exampleValue: '张三',
      },
    })
    std = res.data
    check('创建数标 201', res.status === 201, `${res.status} ${JSON.stringify(res.data)}`)
  } else {
    check('数标已存在，复用', true)
  }
  check('数标列表包含 CUST_NAME', (await call('GET', `/api/standards?${scope}`)).data.some((s) => s.code === 'CUST_NAME'))
  check('数标详情名称=客户名称', (await call('GET', `/api/standards/${std.id}`)).data.content.name === '客户名称')
  const history = (await call('GET', `/api/standards/${std.id}/history`)).data
  check('数标历史非空', Array.isArray(history) && history.length >= 1)
  check('版本校验通过', (await call('GET', `/api/standards/${std.id}/versions/${std.currentVersion}/verify`)).data === true)
  const refs = (await call('GET', `/api/standards/${std.id}/references`)).data
  check('引用接口可用（数组）', Array.isArray(refs))

  // 更新 → 产生新版本（描述带时间戳，保证每次都有内容变化）
  const upd = await call('PUT', `/api/standards/${std.id}`, {
    category: 'CIF',
    content: {
      name: '客户名称',
      description: `客户中文名称（冒烟 ${Date.now()}）`,
      dataType: 'VARCHAR',
      length: 128,
      scale: null,
      required: true,
      defaultValue: null,
      enumValues: null,
      regex: null,
      exampleValue: '李四',
    },
  })
  const newVersion = upd.data?.currentVersion
  check('更新数标产生新版本', newVersion && newVersion !== std.currentVersion, `${std.currentVersion} -> ${newVersion}`)
  const diff = await call('GET', `/api/standards/${std.id}/diff?from=${std.currentVersion}&to=${newVersion}`)
  check('数标差异可用', Array.isArray(diff.data?.changes) && diff.data.changes.length > 0, JSON.stringify(diff.data))
  std = upd.data

  if (std.status === 'DRAFT') {
    const transition = await call('POST', `/api/standards/${std.id}/status?target=PENDING_REVIEW`)
    check('数标流转 草稿→待审', transition.data?.status === 'PENDING_REVIEW', JSON.stringify(transition.data))
  } else {
    check(`数标流转（当前 ${std.status}，跳过）`, true)
  }

  await section('表结构：创建（列引用数标）/ 详情 / DDL / 历史')
  let tblList = (await call('GET', `/api/tables?${scope}`)).data ?? []
  let tbl = listFind(tblList, 'code', 'T_CUSTOMER')
  if (!tbl) {
    const res = await call('POST', `/api/tables?${scope}`, {
      code: 'T_CUSTOMER',
      content: {
        name: 't_customer',
        description: '客户表',
        shardKey: null,
        columns: [
          {
            columnName: 'cust_name',
            standardId: std.id,
            standardCode: 'CUST_NAME',
            dataType: null,
            length: null,
            scale: null,
            nullable: false,
            primaryKey: false,
            defaultValue: null,
            sortOrder: 0,
            comment: '客户名称',
          },
        ],
        indexes: [{ name: 'idx_cust_name', unique: false, columns: ['cust_name'] }],
      },
    })
    tbl = res.data
    check('创建表结构 201', res.status === 201, `${res.status} ${JSON.stringify(res.data)}`)
  } else {
    check('表结构已存在，复用', true)
  }
  const tblDetail = (await call('GET', `/api/tables/${tbl.id}`)).data
  check('表列类型由数标解析(VARCHAR)', tblDetail.content.columns[0].dataType === 'VARCHAR', JSON.stringify(tblDetail.content.columns[0]))
  check('表列长度由数标解析(128)', tblDetail.content.columns[0].length === 128)
  const ddl = await call('GET', `/api/tables/${tbl.id}/ddl`)
  check('DDL 生成含 CREATE TABLE', typeof ddl.data?.ddl === 'string' && /create table/i.test(ddl.data.ddl), JSON.stringify(ddl.data).slice(0, 120))
  check('DDL 含 Flyway 文件名', typeof ddl.data?.flywayFileName === 'string' && ddl.data.flywayFileName.endsWith('.sql'))
  const stdRefs2 = (await call('GET', `/api/standards/${std.id}/references`)).data
  check('数标被表结构引用', Array.isArray(stdRefs2) && stdRefs2.length > 0, JSON.stringify(stdRefs2))

  await section('模板 / 接口 / 生成')
  let tplList = (await call('GET', `/api/templates?${scope}`)).data ?? []
  let tpl = listFind(tplList, 'code', 'TPL_STD')
  if (!tpl) {
    const res = await call('POST', `/api/templates?${scope}`, {
      code: 'TPL_STD',
      content: {
        name: '标准头',
        description: null,
        sections: [
          {
            code: 'HEAD_PROTOCOL',
            name: '协议头',
            fields: [
              { name: 'version', standardId: null, standardCode: null, dataType: 'VARCHAR', length: 8, scale: null, required: true, role: 'HEADER', children: [] },
            ],
          },
        ],
      },
    })
    tpl = res.data
    check('创建模板 201', res.status === 201, `${res.status} ${JSON.stringify(res.data)}`)
  } else check('模板已存在，复用', true)

  let ifaceList = (await call('GET', `/api/interfaces?${scope}`)).data ?? []
  let iface = listFind(ifaceList, 'interfaceNo', 'IF_CUST_QUERY')
  if (!iface) {
    const res = await call('POST', `/api/interfaces?${scope}`, {
      interfaceNo: 'IF_CUST_QUERY',
      templateId: tpl.id,
      content: {
        name: '客户查询',
        businessModule: 'CIF',
        url: '/cif/query',
        shortName: 'cifQuery',
        description: null,
        input: [
          { name: 'custNo', standardId: null, standardCode: null, dataType: 'VARCHAR', length: 32, scale: null, required: true, list: false, children: [] },
        ],
        output: [
          { name: 'custName', standardId: std.id, standardCode: 'CUST_NAME', dataType: null, length: null, scale: null, required: true, list: false, children: [] },
        ],
      },
    })
    iface = res.data
    check('创建接口 201', res.status === 201, `${res.status} ${JSON.stringify(res.data)}`)
  } else check('接口已存在，复用', true)

  const validation = await call('GET', `/api/generation/standards/${std.id}/validation`)
  check('生成校验类', typeof validation.data?.content === 'string' && validation.data.content.length > 0, validation.status)
  const dto = await call('GET', `/api/generation/interfaces/${iface.id}/dto`)
  check('生成 DTO 数组', Array.isArray(dto.data), dto.status)
  const openapi = await call('GET', `/api/generation/openapi?${scope}`)
  const openapiText = typeof openapi.data === 'string' ? openapi.data : JSON.stringify(openapi.data ?? '')
  check('生成 OpenAPI 文档', openapi.status === 200 && /openapi/i.test(openapiText), openapi.status)

  await section('漂移检查')
  const drift = await call('GET', `/api/versioning/drift?${scope}`)
  check('漂移检查返回数组', Array.isArray(drift.data), JSON.stringify(drift.data))

  await section('迁移单：创建 / 提交 / 审批')
  const mig = await call('POST', '/api/migrations', {
    artifactType: 'DATA_STANDARD',
    artifactId: std.id,
    sourceEnvironmentId: envId,
    targetEnvironmentId: test.id,
    note: '冒烟测试迁移',
  })
  check('创建迁移单 201', mig.status === 201, `${mig.status} ${JSON.stringify(mig.data)}`)
  if (mig.data?.id) {
    const submitted = await call('POST', `/api/migrations/${mig.data.id}/submit`)
    check('提交迁移单 → 待审批', submitted.data?.status === 'PENDING_APPROVAL', JSON.stringify(submitted.data))
    const approved = await call('POST', `/api/migrations/${mig.data.id}/approve`)
    check('审批迁移单 → 已批准', approved.data?.status === 'APPROVED', JSON.stringify(approved.data))
  }

  console.log(`\n结果：通过 ${passed}，失败 ${failed}`)
  process.exit(failed > 0 ? 1 : 0)
}

main().catch((err) => {
  console.error('冒烟脚本异常：', err)
  process.exit(1)
})
