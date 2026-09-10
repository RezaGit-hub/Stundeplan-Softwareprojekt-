// URL-Parameter der Plansichten (deutsch: woche, sicht, id, aktion) — Zustand liegt in der URL, nicht in einem Store.
import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { VIEWS, type View, type Weekday } from '@/api/types'
import { addDaysIso, between, startOfIsoWeek } from '@/lib/dates'
import { defaultPlanMonday } from '@/lib/plan-validity'
import { dayOfDate } from '@/lib/timegrid'
import { today } from '@/lib/today'

export function parseView(v: string | null, fallback: View): View {
  return v && (VIEWS as readonly string[]).includes(v) ? (v as View) : fallback
}

/**
 * Immer ein Montag; ungültig oder leer → aktuelle Woche, frühestens aber die erste Woche, in der der
 * aktive Plan gilt (`validFrom`; ohne Angabe wie bisher die Woche von heute).
 */
export function parseMonday(v: string | null, validFrom: string | null = null): string {
  return v && /^\d{4}-\d{2}-\d{2}$/.test(v) ? startOfIsoWeek(v) : defaultPlanMonday(today(), validFrom)
}

export function parseId(v: string | null): number | null {
  const n = v ? Number(v) : NaN
  return Number.isFinite(n) && n > 0 ? n : null
}

/** Wochentag von „heute", wenn er in der Woche ab monday liegt */
export function todayDayIn(monday: string): Weekday | undefined {
  const t = today()
  return between(t, monday, addDaysIso(monday, 6)) ? (dayOfDate(t) ?? undefined) : undefined
}

/** get/set für die Suchparameter; set ersetzt den History-Eintrag (Sichtwechsel füllen die Historie nicht) */
/** Was `useUrlState().set` annimmt — als eigener Name, damit Seiten ihn weiterreichen können. */
export type UrlSetter = (patch: Record<string, string | null | undefined>) => void

export function useUrlState() {
  const [params, setParams] = useSearchParams()
  const set = useCallback((patch: Record<string, string | null | undefined>) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [k, v] of Object.entries(patch)) {
        if (v == null || v === '') next.delete(k)
        else next.set(k, v)
      }
      return next
    }, { replace: true })
  }, [setParams])
  return { params, set }
}
