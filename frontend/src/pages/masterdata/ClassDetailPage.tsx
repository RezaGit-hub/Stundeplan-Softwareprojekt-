// /grunddaten/klassen/:id — Kopf mit Kennzahlen + Fächerzuordnung (ClassSubjectsTable).
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, BookOpen, Clock, Pencil } from 'lucide-react'
import { useClass, useSubjects, useTeachers } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { StatTile } from '@/components/stats/StatTile'
import { ClassDialog } from '@/components/masterdata/ClassDialog'
import { ClassSubjectsTable } from '@/components/masterdata/ClassSubjectsTable'
import { classSignature, weeklyHoursOf } from '@/components/masterdata/helpers'
import { ActiveBadge, SectionCard } from '@/components/masterdata/shared'

export function ClassDetailPage() {
  const { id } = useParams()
  const classId = id && /^\d+$/.test(id) ? Number(id) : null
  const schoolClass = useClass(classId)
  const subjects = useSubjects()
  const teachers = useTeachers()
  const [editOpen, setEditOpen] = useState(false)
  const [editKey, setEditKey] = useState(0)
  const openEdit = () => { setEditKey((k) => k + 1); setEditOpen(true) }

  if (schoolClass.isPending || subjects.isPending || teachers.isPending) return <PageSkeleton />
  if (schoolClass.isError) return <ErrorState problem={schoolClass.error} retry={() => void schoolClass.refetch()} />
  if (subjects.isError) return <ErrorState problem={subjects.error} retry={() => void subjects.refetch()} />
  if (teachers.isError) return <ErrorState problem={teachers.error} retry={() => void teachers.refetch()} />

  const c = schoolClass.data
  const hours = weeklyHoursOf(c)

  return (
    <>
      <PageHeader
        title={`Klasse ${c.name}`}
        description={`Jahrgang ${c.grade} · ${c.subjects.length} Fächer · ${hours} Wochenstunden`}
        actions={
          <>
            <ActiveBadge active={c.active} />
            <Button variant="outline" asChild><Link to="/grunddaten/klassen"><ArrowLeft /> Alle Klassen</Link></Button>
            <Button variant="outline" onClick={openEdit}><Pencil /> Bearbeiten</Button>
          </>
        }
      />

      <div className="mb-5 grid gap-3 sm:grid-cols-3">
        <StatTile label="Fächer" value={c.subjects.length} icon={BookOpen} hint={c.subjects.length === 0 ? 'noch keine Zuordnung' : `${new Set(c.subjects.map((r) => r.teacherId)).size} Lehrkräfte`} />
        <StatTile label="Wochenstunden" value={hours} icon={Clock} hint="Summe über alle Fächer" />
        <StatTile label="Hinweise" value={c.warnings.length} icon={AlertTriangle} tone={c.warnings.length > 0 ? 'warning' : 'default'} hint={c.warnings.length > 0 ? 'Speichern bleibt möglich' : 'keine Warnungen'} />
      </div>

      <SectionCard
        title="Fächer und Lehrkräfte"
        description="Jedes Fach mit Wochenstunden und der zuständigen Lehrkraft. Fachlehrkräfte stehen in der Auswahl zuerst; deaktivierte Lehrkräfte fehlen."
      >
        <ClassSubjectsTable key={classSignature(c)} schoolClass={c} subjects={subjects.data} teachers={teachers.data} />
      </SectionCard>

      <ClassDialog key={`${c.id}-${editKey}`} open={editOpen} schoolClass={c} onOpenChange={setEditOpen} />
    </>
  )
}
