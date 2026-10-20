import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import * as React from 'react'

import { cn } from '@/lib/utils'

/**
 * GitHub 式按钮：默认态是灰底描边，主操作走绿色，危险操作走红色。
 * 所有按钮共享 6px 圆角、1px 边框和极轻的下边阴影。
 */
const buttonVariants = cva(
  'inline-flex select-none items-center justify-center gap-1.5 whitespace-nowrap rounded-md border text-[13px] font-medium leading-none shadow-sm transition-[color,background-color,border-color,box-shadow] duration-150 outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:pointer-events-none disabled:opacity-60 active:shadow-none [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default:
          'border-border bg-secondary text-secondary-foreground hover:border-border hover:bg-accent',
        primary:
          'border-status-success-emphasis bg-status-success-emphasis text-white hover:border-status-success-emphasis-hover hover:bg-status-success-emphasis-hover',
        secondary:
          'border-border bg-secondary text-secondary-foreground hover:border-border hover:bg-accent',
        outline:
          'border-border bg-card text-foreground hover:border-border hover:bg-accent hover:text-accent-foreground',
        ghost:
          'border-transparent bg-transparent text-foreground shadow-none hover:bg-accent hover:text-accent-foreground',
        destructive:
          'border-destructive bg-destructive text-destructive-foreground hover:border-destructive-emphasis hover:bg-destructive-emphasis',
        link:
          'border-transparent bg-transparent text-primary shadow-none underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-8 px-3',
        sm: 'h-7 px-2.5 text-[12px]',
        lg: 'h-9 px-4 text-[14px]',
        icon: 'h-8 w-8 p-0',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button'
    return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />
  },
)
Button.displayName = 'Button'

export { Button, buttonVariants }
