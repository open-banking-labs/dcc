// 本地开发用：签发一个 HS256 JWT，供前端联调/curl 使用。
// 后端默认密钥见 dcc-web application.yml（DCC_JWT_SECRET，未设置时用默认值）。
//
//   node scripts/dev-token.mjs
//
// 环境变量可覆盖：DCC_JWT_SECRET / DCC_TENANT / DCC_ROLES / DCC_SUB
import crypto from 'node:crypto'

const secret = process.env.DCC_JWT_SECRET ?? 'dev-only-change-me-please-use-32-bytes'
const tenant = process.env.DCC_TENANT ?? 'default_tenant'
const roles = (process.env.DCC_ROLES ?? 'DCC_TENANT_ADMIN').split(',').map((r) => r.trim()).filter(Boolean)
const sub = process.env.DCC_SUB ?? 'local-dev'

const base64url = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url')
const now = Math.floor(Date.now() / 1000)

const header = { alg: 'HS256', typ: 'JWT' }
const payload = { sub, tenant, roles, iat: now, exp: now + 60 * 60 * 8 }

const unsigned = `${base64url(header)}.${base64url(payload)}`
const signature = crypto.createHmac('sha256', secret).update(unsigned).digest('base64url')

process.stdout.write(`${unsigned}.${signature}\n`)
