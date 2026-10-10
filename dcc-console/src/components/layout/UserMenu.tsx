import { LogOut, UserRound } from 'lucide-react'

import { useSession } from '@/auth/session'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

/** 顶栏用户菜单：显示身份与角色，提供退出。 */
export function UserMenu() {
  const session = useSession()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="flex items-center gap-1.5 rounded-md px-2 py-1 text-sm text-foreground hover:bg-accent"
        >
          <UserRound className="h-4 w-4" />
          <span className="max-w-[8rem] truncate">{session.displayName}</span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-52">
        <DropdownMenuLabel>
          <div className="truncate text-sm">{session.displayName}</div>
          {session.roles.length > 0 && (
            <div className="mt-0.5 text-xs font-normal text-muted-foreground">
              {session.roles.join('、')}
            </div>
          )}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => session.signout()}>
          <LogOut className="mr-2 h-4 w-4" />
          退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
