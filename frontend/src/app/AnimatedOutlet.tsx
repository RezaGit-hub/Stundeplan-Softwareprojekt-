// Seitenwechsel mit 200 ms Opacity + 6 px (lib/motion.ts). AnimatePresence mode="wait" um useOutlet().
//
// Das Suspense liegt INNERHALB der bewegten Ebene: die Seiten werden erst beim Aufruf geladen
// (routes/lazy-page.ts), und der Platzhalter soll mit derselben Bewegung erscheinen wie die Seite
// danach — sonst blitzt bei jedem ersten Aufruf einer Seite ein ungebremster Wechsel auf.
import { Suspense } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { useLocation, useOutlet } from 'react-router-dom'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { pageTransition } from '@/lib/motion'

export function AnimatedOutlet() {
  const outlet = useOutlet()
  const location = useLocation()
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div key={location.pathname} {...pageTransition} className="min-h-0 flex-1">
        <Suspense fallback={<PageSkeleton />}>{outlet}</Suspense>
      </motion.div>
    </AnimatePresence>
  )
}
