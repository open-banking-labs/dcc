import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const RETURN_KEY = 'dcc.standards.returnTo'

const scrollKey = (url: string) => `dcc.scroll:${url}`

/**
 * 列表页的状态恢复：
 * - 把当前列表 URL 记到 sessionStorage，详情页的“返回列表”据此回到原筛选/页码；
 * - 离开列表时保存滚动位置，数据重新加载完成后恢复。
 *
 * 查询页与维护页共用；`ready` 传 `!loading`，确保列表有高度后再滚动。
 */
export function useListRestoration(ready: boolean) {
  const location = useLocation()
  const url = location.pathname + location.search

  useEffect(() => {
    sessionStorage.setItem(RETURN_KEY, url)
  }, [url])

  useEffect(() => {
    const key = scrollKey(url)
    return () => {
      sessionStorage.setItem(key, String(window.scrollY))
    }
  }, [url])

  useEffect(() => {
    if (!ready) return
    const saved = sessionStorage.getItem(scrollKey(url))
    if (saved) window.scrollTo(0, Number(saved))
  }, [ready, url])
}

/** 详情页的返回地址：只在应用内列表路径上生效，避免开放重定向。 */
export function returnToListUrl(): string {
  const stored = sessionStorage.getItem(RETURN_KEY)
  return stored && stored.startsWith('/standards') ? stored : '/standards'
}
