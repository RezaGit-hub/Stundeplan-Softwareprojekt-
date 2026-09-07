// Sichtwechsel Schule · Klasse · Lehrkraft · Raum als Segmented Control (shadcn Tabs, aktiver Reiter gleitet per layoutId).
import { motion, useReducedMotion } from 'motion/react'
import { DoorOpen, GraduationCap, School, Users, type LucideIcon } from 'lucide-react'
import type { View } from '@/api/types'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { BASE, EASE } from '@/lib/motion'
import { VIEW_LABEL } from './object-options'

const ITEMS: { value: View; icon: LucideIcon }[] = [
  { value: 'SCHOOL', icon: School },
  { value: 'CLASS', icon: GraduationCap },
  { value: 'TEACHER', icon: Users },
  { value: 'ROOM', icon: DoorOpen },
]

export function ViewSwitch({ value, onChange, layoutId = 'view-switch' }: { value: View; onChange(v: View): void; layoutId?: string }) {
  const reduce = useReducedMotion()
  return (
    <Tabs value={value} onValueChange={(v) => onChange(v as View)}>
      <TabsList aria-label="Sicht" className="h-8 border bg-surface-sunken/70 shadow-xs">
        {ITEMS.map((it) => (
          <TabsTrigger
            key={it.value}
            value={it.value}
            className="relative px-2.5 data-active:bg-transparent data-active:shadow-none dark:data-active:border-transparent dark:data-active:bg-transparent"
          >
            {value === it.value && (
              <motion.span
                layoutId={layoutId}
                className="absolute inset-0 rounded-md border border-border/70 bg-background shadow-xs dark:bg-input/40"
                transition={reduce ? { duration: 0 } : { duration: BASE, ease: EASE }}
                aria-hidden
              />
            )}
            <span className="relative z-10 inline-flex items-center gap-1.5">
              <it.icon className="size-3.5" aria-hidden />
              {VIEW_LABEL[it.value]}
            </span>
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )
}
