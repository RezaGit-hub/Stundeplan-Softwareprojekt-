// Einstiegspunkt. Im Attrappen-Modus (VITE_MOCK=true) wird die Attrappe erst hier nachgeladen und in
// `api.ts` angemeldet — so bleibt sie aus dem Produktionsbundle heraus (Befund FRONTEND-3, 05.09.).
import '@fontsource-variable/inter'
import './index.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { ThemeProvider } from '@/components/theme/theme-provider'
import { AuthProvider } from '@/app/auth'
import { queryClient } from '@/api/hooks'
import { IS_MOCK, setApi } from '@/api/api'
import { router } from '@/app/router'

const app = (
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <TooltipProvider delayDuration={300}>
            <RouterProvider router={router} />
            <Toaster position="bottom-right" richColors={false} closeButton />
          </TooltipProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>
)

async function start() {
  if (IS_MOCK) {
    const { mockApi } = await import('@/mock/mock-api')
    setApi(mockApi)
  }
  createRoot(document.getElementById('root')!).render(app)
}

void start()
