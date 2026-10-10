import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'

import { AuthTokenSync } from './auth/AuthTokenSync'
import { SessionProvider } from './auth/session'
import { router } from './router'
import './styles/globals.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionProvider>
      <AuthTokenSync />
      <RouterProvider router={router} />
    </SessionProvider>
  </StrictMode>,
)
