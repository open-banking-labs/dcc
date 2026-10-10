import type { AuthProviderProps } from 'react-oidc-context'

const issuer = import.meta.env.VITE_OIDC_ISSUER
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID
const redirectUri =
  import.meta.env.VITE_OIDC_REDIRECT_URI ?? `${window.location.origin}/auth/callback`
const scope = import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile'

/** 必填项是否就绪；缺省时应用渲染配置提示页而非发起发现请求。 */
export const oidcConfigured = Boolean(issuer && clientId)

export const oidcConfig: AuthProviderProps = {
  authority: issuer ?? '',
  client_id: clientId ?? '',
  redirect_uri: redirectUri,
  post_logout_redirect_uri: window.location.origin,
  scope,
  response_type: 'code',
  automaticSilentRenew: true,
  // 回调换票完成后清掉 ?code=&state=，回到干净的 SPA 路由。
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
