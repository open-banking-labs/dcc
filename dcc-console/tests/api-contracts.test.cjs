const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')
const ts = require('typescript')

function load(relativePath, dependencies) {
  const source = readFileSync(path.join(__dirname, '../src', relativePath), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  })
  const exports = {}
  vm.runInNewContext(outputText, {
    exports,
    require: (name) => {
      assert.ok(name in dependencies, `Unexpected dependency: ${name}`)
      return dependencies[name]
    },
  })
  return exports
}

function captureHttp() {
  const requests = []
  const http = Object.fromEntries(['Get', 'Post', 'Delete'].map((method) => [
    `http${method}`, async (url, ...args) => {
      requests.push(JSON.parse(JSON.stringify({ method, url, args })))
      return { permissions: ['artifact.read'] }
    },
  ]))
  return { requests, http }
}

const { PERMISSION } = load('lib/permissions.ts', {})
const auditDependencies = (http) => ({ '@/lib/apiClient': http, '@/lib/permissions': { PERMISSION } })

test('tenant directory never sends a caller-controlled tenant in queries or grants', async () => {
  const { requests, http } = captureHttp()
  const { tenantDirectoryApi: api } = load('api/directory.ts', { '@/lib/apiClient': http })
  await api.memberships.search('alice', 'other-tenant', 0, 20)
  await api.memberships.create({ subject: 'alice', tenantCode: 'other-tenant' })
  await api.assignments.create({ subject: 'alice', tenantCode: 'other-tenant', role: 'DCC_APPROVER', scopeType: 'ENVIRONMENT', scopeCode: 'TEST' })
  assert.equal(requests[0].url, '/memberships')
  assert.deepEqual(requests[0].args[0].params, { subject: 'alice', page: 0, size: 20 })
  assert.deepEqual(requests[1].args[0], { subject: 'alice' })
  assert.equal(requests[2].url, '/role-assignments')
  assert.deepEqual(requests[2].args[0], { subject: 'alice', role: 'DCC_APPROVER', scopeType: 'ENVIRONMENT', scopeCode: 'TEST' })
  await api.assignments.confirm(7)
  await api.memberships.restore(8)
  assert.equal(requests[3].url, '/role-assignments/7/confirm')
  assert.equal(requests[4].url, '/memberships/8/restore')
})

test('platform directory preserves explicit tenant and platform endpoint', async () => {
  const { requests, http } = captureHttp()
  const { directoryApi: api } = load('api/directory.ts', { '@/lib/apiClient': http })
  await api.memberships.create({ subject: 'alice', tenantCode: 'target' })
  await api.assignments.search(null, 'target', null, null, null, 0, 20)
  assert.equal(requests[0].url, '/directory/memberships')
  assert.equal(requests[0].args[0].tenantCode, 'target')
  assert.equal(requests[1].url, '/directory/role-assignments')
  assert.equal(requests[1].args[0].params.tenant, 'target')
})

test('business and platform audit use distinct backend readers', async () => {
  const { requests, http } = captureHttp()
  const { auditApi } = load('api/audit.ts', auditDependencies(http))
  await auditApi.search({ actor: '', page: 0 })
  await auditApi.search({ actor: 'alice', page: 0 }, true)
  assert.equal(requests[0].url, '/audit')
  assert.equal(requests[1].url, '/platform/audit')
  assert.deepEqual(requests[1].args[0].params, { actor: 'alice', page: 0 })
})

test('audit restore actions follow each backend resource permission', () => {
  const { hasRestorePermission } = load('api/audit.ts', auditDependencies({}))
  assert.equal(hasRestorePermission('DataStandard', ['artifact.restore']), true)
  assert.equal(hasRestorePermission('TenantEnvironment', ['artifact.restore']), false)
  assert.equal(hasRestorePermission('TenantEnvironment', ['topology.write']), true)
  assert.equal(hasRestorePermission('Tenant', ['tenant.admit']), true)
  assert.equal(hasRestorePermission('RoleAssignment', ['directory.write']), false)
  assert.equal(hasRestorePermission('RoleAssignment', ['directory.write.platform']), true)
  assert.equal(hasRestorePermission('Unknown', ['artifact.restore']), false)
})

test('effective permissions cannot leak across token changes or failed requests', async () => {
  const states = []
  let cursor = 0
  let effect
  const pending = []
  const { useEffectivePermissions } = load('api/me.ts', {
    react: {
      useState: (initial) => {
        const index = cursor++
        if (states[index] === undefined) states[index] = initial
        return [states[index], (next) => {
          states[index] = typeof next === 'function' ? next(states[index]) : next
        }]
      },
      useEffect: (next) => { effect = next },
    },
    '@/lib/apiClient': {
      httpGet: (url, config) => new Promise((resolve, reject) => pending.push({ url, config, resolve, reject })),
    },
  })
  const render = (token) => {
    cursor = 0
    const result = useEffectivePermissions(token)
    return { permissions: Array.from(result.permissions), loading: result.loading }
  }
  assert.deepEqual(render('first'), { permissions: [], loading: true })
  const cleanup = effect()
  assert.equal(pending[0].config.headers.Authorization, 'Bearer first')
  pending[0].resolve({ permissions: ['directory.write.platform'] })
  await new Promise(setImmediate)
  assert.deepEqual(render('first'), { permissions: ['directory.write.platform'], loading: false })
  assert.deepEqual(render('second'), { permissions: [], loading: true })
  cleanup()
  effect()
  pending[1].reject(new Error('offline'))
  await new Promise(setImmediate)
  assert.deepEqual(render('second'), { permissions: [], loading: false })
  assert.deepEqual(render(null), { permissions: [], loading: false })
})
