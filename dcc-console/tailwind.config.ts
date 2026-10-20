import animate from 'tailwindcss-animate'
import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        canvas: 'hsl(var(--canvas))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
          emphasis: 'hsl(var(--destructive-emphasis))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        // 随主题切换的顶栏。
        header: {
          DEFAULT: 'hsl(var(--header))',
          foreground: 'hsl(var(--header-foreground))',
          muted: 'hsl(var(--header-muted))',
          border: 'hsl(var(--header-border))',
          brand: 'hsl(var(--header-brand))',
          'brand-foreground': 'hsl(var(--header-brand-foreground))',
        },
        sidebar: {
          DEFAULT: 'hsl(var(--sidebar))',
          accent: 'hsl(var(--sidebar-accent))',
          foreground: 'hsl(var(--sidebar-foreground))',
        },
        // 状态语义色：每种都配套浅底，状态同时用图标 + 文字表达，不单靠颜色。
        status: {
          neutral: 'hsl(var(--neutral))',
          'neutral-soft': 'hsl(var(--neutral-soft))',
          success: 'hsl(var(--success))',
          'success-soft': 'hsl(var(--success-soft))',
          'success-emphasis': 'hsl(var(--success-emphasis))',
          'success-emphasis-hover': 'hsl(var(--success-emphasis-hover))',
          warning: 'hsl(var(--warning))',
          'warning-soft': 'hsl(var(--warning-soft))',
          info: 'hsl(var(--info))',
          'info-soft': 'hsl(var(--info-soft))',
          danger: 'hsl(var(--danger))',
          'danger-soft': 'hsl(var(--danger-soft))',
          'danger-emphasis': 'hsl(var(--danger-emphasis))',
          done: 'hsl(var(--done))',
          'done-soft': 'hsl(var(--done-soft))',
        },
        'accent-line': 'hsl(var(--accent-line))',
        scrim: 'var(--scrim)',
      },
      // GitHub 的容器 / 控件统一 6px 圆角；小标签才用 4px。
      borderRadius: {
        lg: 'var(--radius)',
        md: 'var(--radius)',
        sm: 'calc(var(--radius) - 2px)',
      },
      // 页面表面保持平面；只给按钮、输入框和浮层极轻的层次。
      boxShadow: {
        none: 'none',
        sm: 'var(--shadow-sm)',
        DEFAULT: 'var(--shadow-sm)',
        md: 'var(--shadow-md)',
        lg: 'var(--shadow-lg)',
        xl: 'var(--shadow-lg)',
        '2xl': 'var(--shadow-lg)',
        inner: 'none',
        popover: 'var(--shadow-popover)',
      },
      fontFamily: {
        sans: [
          '-apple-system',
          'BlinkMacSystemFont',
          '"Segoe UI"',
          '"Noto Sans"',
          'Helvetica',
          'Arial',
          '"PingFang SC"',
          '"Hiragino Sans GB"',
          '"Microsoft YaHei"',
          '"Noto Sans SC"',
          'sans-serif',
        ],
        mono: [
          'ui-monospace',
          'SFMono-Regular',
          '"SF Mono"',
          'Menlo',
          'Consolas',
          '"Liberation Mono"',
          'monospace',
        ],
      },
      transitionTimingFunction: {
        'out-quad': 'cubic-bezier(0.22, 0.61, 0.36, 1)',
      },
    },
  },
  plugins: [animate],
}

export default config
