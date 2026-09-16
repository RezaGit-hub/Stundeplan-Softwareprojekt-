// /einstellungen?bereich=schule|beispieldaten|konten|passwort|ferien — Tabs mit Systemkonfiguration, Beispieldaten,
// Benutzerkonten, eigenem Passwort und unterrichtsfreien Zeiträumen (Kann).
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { CalendarOff, Compass, Database, KeyRound, Plus, Save, School, UserRound, Users, Trash2 } from 'lucide-react'
import { useConfig, useLoadHolidayPreset, usePutConfig, useSetTour, useTeachers, useUsers } from '@/api/hooks'
import type { ConfigDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/layout/PageHeader'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { EmptyActivePlanCard } from '@/components/masterdata/EmptyActivePlanCard'
import { HolidaysTable } from '@/components/masterdata/HolidaysTable'
import { SampleDataCard } from '@/components/masterdata/SampleDataCard'
import { ClassAccountsDialog } from '@/components/masterdata/ClassAccountsDialog'
import { UserDialog } from '@/components/masterdata/UserDialog'
import { UsersTable } from '@/components/masterdata/UsersTable'
import { TeacherAccountsDialog } from '@/components/masterdata/TeacherAccountsDialog'
import { DataDeletionTab } from '@/components/masterdata/DataDeletionTab'
import { presetToast, SCHOOL_TYPE_LABEL, STATE_LABEL } from '@/components/masterdata/helpers'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { Field, SectionCard } from '@/components/masterdata/shared'
import { PasswordChangeFields } from '@/components/forms/PasswordChangeFields'
import { usePasswordChange } from '@/lib/password-change'
import { schoolYearOf } from '@/lib/dates'
import { useFieldErrors } from '@/lib/form-errors'
import { today } from '@/lib/today'

const TABS = [
  { id: 'schule', label: 'Schule', icon: School },
  { id: 'beispieldaten', label: 'Beispieldaten', icon: Database },
  { id: 'konten', label: 'Benutzerkonten', icon: UserRound },
  { id: 'passwort', label: 'Eigenes Passwort', icon: KeyRound },
  { id: 'ferien', label: 'Unterrichtsfreie Zeiträume', icon: CalendarOff },
  { id: 'loeschen', label: 'Grunddaten löschen', icon: Trash2 },
] as const
type TabId = (typeof TABS)[number]['id']

export function SettingsPage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('bereich')
  const tab: TabId = TABS.some((t) => t.id === raw) ? (raw as TabId) : 'schule'

  return (
    <>
      <PageHeader title="Einstellungen" description="Systemkonfiguration, Beispieldaten, Konten und unterrichtsfreie Zeiträume" />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'schule' ? {} : { bereich: v }, { replace: true })}>
        <TabsList variant="line" className="mb-4 w-full justify-start border-b pb-0">
          {TABS.map((t) => (
            <TabsTrigger key={t.id} value={t.id} className="gap-1.5"><t.icon className="size-3.5" aria-hidden />{t.label}</TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="schule" className="space-y-4"><SchoolTab /><TourCard /></TabsContent>
        <TabsContent value="beispieldaten" className="space-y-4"><SampleDataCard /><EmptyActivePlanCard /></TabsContent>
        <TabsContent value="konten"><AccountsTab /></TabsContent>
        <TabsContent value="passwort"><PasswordTab /></TabsContent>
        <TabsContent value="ferien"><SectionCard title="Unterrichtsfreie Zeiträume" description="Ferien und Feiertage (Kann-Anforderung). Für das eingestellte Bundesland liegt eine Vorlage bei."><HolidaysTable /></SectionCard></TabsContent>
        <TabsContent value="loeschen"><DataDeletionTab /></TabsContent>
      </Tabs>
    </>
  )
}

// ------------------------------------------------------------------ Schule
function SchoolTab() {
  const config = useConfig()
  if (config.isPending) return <PageSkeleton />
  if (config.isError) return <ErrorState problem={config.error} retry={() => void config.refetch()} />
  return <SchoolForm key={JSON.stringify(config.data)} config={config.data} />
}

function SchoolForm({ config }: { config: ConfigDto }) {
  const put = usePutConfig()
  const loadPreset = useLoadHolidayPreset()
  const fields = useFieldErrors()
  const [form, setForm] = useState<ConfigDto>(config)
  // Nach dem Speichern mit geändertem Bundesland: Rückfrage, ob die mitgelieferten Ferien und
  // Feiertage dieses Landes übernommen werden sollen (E-51). Das Schuljahr kommt aus dem Datum.
  const [askPreset, setAskPreset] = useState<string | null>(null)
  const set = (p: Partial<ConfigDto>) => setForm((f) => ({ ...f, ...p }))
  const dirty = JSON.stringify(form) !== JSON.stringify(config)
  const schoolYear = schoolYearOf(today())

  const save = async () => {
    fields.clear()
    const stateChanged = form.state !== config.state
    try {
      await put.mutateAsync(form)
      toast.success('Einstellungen gespeichert')
      if (stateChanged) setAskPreset(form.state)
    } catch (err) {
      fields.handle(err)
    }
  }

  const uebernehmen = async () => {
    try {
      const res = await loadPreset.mutateAsync({ schoolYear })
      toast.success(presetToast(res.added, res.skipped))
    } catch { /* Toast aus dem Hook; „Später" ist immer möglich */ }
  }

  const stateOptions = Object.keys(STATE_LABEL).includes(form.state) ? Object.keys(STATE_LABEL) : [form.state, ...Object.keys(STATE_LABEL)]
  const typeOptions = Object.keys(SCHOOL_TYPE_LABEL).includes(form.schoolType) ? Object.keys(SCHOOL_TYPE_LABEL) : [form.schoolType, ...Object.keys(SCHOOL_TYPE_LABEL)]

  return (
    <form onSubmit={(e) => { e.preventDefault(); void save() }}>
      <SectionCard
        title="Schule"
        description="Name, Bundesland und Schulform sind Daten — die Software ist nicht auf eine Schulform festgelegt. Bundesland und Schulform bestimmen den Beispielsatz im Regelwerk."
        actions={<Button type="submit" disabled={!dirty || put.isPending}><Save /> Speichern</Button>}
      >
        <div className="grid max-w-2xl gap-4">
          <Field label="Name der Schule" htmlFor="cfg-name" error={fields.errors.schoolName} hint="Erscheint in der Sidebar, auf der Anmeldeseite und in der App.">
            <Input id="cfg-name" value={form.schoolName} onChange={(e) => set({ schoolName: e.target.value })} aria-invalid={!!fields.errors.schoolName} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Bundesland" error={fields.errors.state}>
              <Select value={form.state} onValueChange={(v) => set({ state: v })}>
                <SelectTrigger className="w-full" aria-label="Bundesland"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {stateOptions.map((s) => <SelectItem key={s} value={s}>{STATE_LABEL[s] ?? s}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Schulform" error={fields.errors.schoolType}>
              <Select value={form.schoolType} onValueChange={(v) => set({ schoolType: v })}>
                <SelectTrigger className="w-full" aria-label="Schulform"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {typeOptions.map((s) => <SelectItem key={s} value={s}>{SCHOOL_TYPE_LABEL[s] ?? s}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          </div>
          <Field label="Rechenzeit der Berechnung" htmlFor="cfg-solve" error={fields.errors.maxSolveSeconds} hint="Sekunden, die der Solver höchstens rechnet (Entwurf → Berechnen). Mehr Zeit = bessere Pläne bei großen Schulen.">
            <div className="flex items-center gap-2">
              <Input id="cfg-solve" type="number" min={1} value={form.maxSolveSeconds} onChange={(e) => set({ maxSolveSeconds: Number(e.target.value) })} className="w-28 tabular-nums" aria-invalid={!!fields.errors.maxSolveSeconds} />
              <span className="text-sm text-foreground-subtle">Sekunden</span>
            </div>
          </Field>
        </div>
      </SectionCard>
      <ConfirmDialog
        open={askPreset != null}
        onOpenChange={(o) => { if (!o) setAskPreset(null) }}
        title={`Ferien und Feiertage ${STATE_LABEL[askPreset ?? ''] ?? askPreset ?? ''} ${schoolYear} übernehmen?`}
        description={'Die Zeiträume sind mitgeliefert — es wird nichts aus dem Internet geholt. Was schon eingetragen ist, bleibt unverändert; jeder übernommene Eintrag ist danach einzeln änderbar und löschbar. Weitere Schuljahre gibt es im Bereich „Unterrichtsfreie Zeiträume".'}
        confirmLabel="Übernehmen"
        cancelLabel="Später"
        onConfirm={uebernehmen}
      />
    </form>
  )
}

// ------------------------------------------------------------------ Rundgang
// „Rundgang erneut zeigen" (#139): löscht tourCompletedAt am Konto (PUT /api/me/tour {completed: false});
// die Shell lädt /me neu und startet den geführten Rundgang wieder bei Schritt 1.
function TourCard() {
  const setTour = useSetTour()
  const zeigen = async () => {
    try {
      await setTour.mutateAsync({ completed: false })
      toast.success('Der Rundgang beginnt wieder bei Schritt 1.')
    } catch { /* Toast aus dem Hook */ }
  }
  return (
    <SectionCard
      title="Rundgang"
      description="Führt einmal durch den ganzen Weg: Grunddaten anlegen, Plan berechnen, Konflikt auflösen, annehmen, Vertretung und Ereignisse. Erscheint beim ersten Anmelden der Planung und lässt sich hier jederzeit neu starten."
      actions={<Button variant="outline" onClick={() => void zeigen()} disabled={setTour.isPending}><Compass /> Rundgang erneut zeigen</Button>}
    >
      <p className="text-sm text-foreground-subtle">Der Stand ist am Konto gespeichert, nicht im Browser: an einem anderen Rechner erscheint der Rundgang nicht noch einmal.</p>
    </SectionCard>
  )
}

// ------------------------------------------------------------------ Konten
function AccountsTab() {
  const users = useUsers()
  const teachers = useTeachers()
  const [open, setOpen] = useState(false)
  const [key, setKey] = useState(0)
  const [classOpen, setClassOpen] = useState(false)
  const [classKey, setClassKey] = useState(0)
  const [teacherOpen, setTeacherOpen] = useState(false)
  const [teacherKey, setTeacherKey] = useState(0)
  if (users.isPending || teachers.isPending) return <PageSkeleton />
  if (users.isError) return <ErrorState problem={users.error} retry={() => void users.refetch()} />
  if (teachers.isError) return <ErrorState problem={teachers.error} retry={() => void teachers.refetch()} />
  const planning = users.data.filter((u) => u.role === 'PLANNING').length
  const classAccounts = users.data.filter((u) => u.role === 'STUDENT').length
  const teacherAccounts = users.data.length - planning - classAccounts
  return (
    <SectionCard
      title="Benutzerkonten"
      description={`${users.data.length} Konten · ${planning} Planung · ${teacherAccounts} Lehrkräfte · ${classAccounts} Klassen. Lehrkräfte sehen im Web nur ihren eigenen Plan, ein Klassenkonto nur den Plan seiner Klasse.`}
      actions={
        <div className="flex flex-wrap gap-2">
          {/* Der Massenweg für die Schülersicht (E-50); der Dialog zeigt die Passwörter genau einmal. */}
          <Button variant="outline" onClick={() => { setClassKey((k) => k + 1); setClassOpen(true) }}><Users /> Konten für alle Klassen anlegen</Button>
          <Button variant="outline" onClick={() => { setTeacherKey((k) => k + 1); setTeacherOpen(true) }}><Users /> Konten für alle Lehrkräfte anlegen</Button>
          <Button onClick={() => { setKey((k) => k + 1); setOpen(true) }}><Plus /> Neues Konto</Button>
        </div>
      }
    >
      <UsersTable users={users.data} teachers={teachers.data} />
      <UserDialog key={key} open={open} onOpenChange={setOpen} users={users.data} teachers={teachers.data} />
      <ClassAccountsDialog key={classKey} open={classOpen} onOpenChange={setClassOpen} />
      <TeacherAccountsDialog key={teacherKey} open={teacherOpen} onOpenChange={setTeacherOpen} />
    </SectionCard>
  )
}

// ------------------------------------------------------------------ Eigenes Passwort
// Felder und Prüfung stehen in components/forms/password-change.tsx — dieselben, die auch das
// Benutzermenü der Kopfleiste benutzt. Vorher gab es zwei Formulare mit zwei Verhalten (FRONTEND-8).
function PasswordTab() {
  const pw = usePasswordChange()

  return (
    <form onSubmit={(e) => { e.preventDefault(); void pw.save() }}>
      <SectionCard title="Eigenes Passwort" description="Gilt für die Anmeldung im Web und in der App." actions={<Button type="submit" disabled={pw.pending || !pw.ready}><Save /> Passwort ändern</Button>}>
        <div className="grid max-w-md gap-4">
          <PasswordChangeFields state={pw} idPrefix="pw-settings" />
        </div>
      </SectionCard>
    </form>
  )
}
