// /entwurf — die Entwurfsliste (#103). Seit 1.3.0 gibt es mehrere Entwürfe nebeneinander: Varianten
// vergleichen, eine annehmen, die anderen stehen lassen oder verwerfen. Die Liste zeigt je Entwurf
// Name, Anlage, Quelle, Status, harte Konflikte und ob er in der Ablage liegt (#116).
//
// Die Zeilenaktionen stehen in einer eigenen Komponente je Zeile: ihre Mutationen hängen an der Kennung
// des Entwurfs (`usePatchDraft(id)`), und Hooks dürfen nicht in einer Schleife der Seite entstehen.
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Archive, ArchiveRestore, Copy, FilePlus, Pencil, PencilRuler, Plus, Trash2 } from 'lucide-react'
import { useDeleteDraft, useDrafts, usePatchDraft } from '@/api/hooks'
import type { DraftSummaryDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { NewDraftDialog } from '@/components/draft/NewDraftDialog'
import { RenameDraftDialog } from '@/components/draft/RenameDraftDialog'
import { useUrlState } from '@/components/plan/plan-params'
import { formatDateTime } from '@/lib/dates'

const SOURCE_LABEL: Record<DraftSummaryDto['source'], string> = {
  EMPTY: 'leer begonnen',
  COPY_ACTIVE: 'Kopie des aktiven Plans',
}

function StatusBadge({ draft }: { draft: DraftSummaryDto }) {
  if (draft.status === 'SOLVING') return <Badge variant="secondary">rechnet …</Badge>
  if (draft.status === 'FAILED') return <Badge variant="destructive">Berechnung fehlgeschlagen</Badge>
  if (draft.finished) return <Badge variant="outline"><Archive aria-hidden /> Ablage</Badge>
  return <Badge variant="outline">in Arbeit</Badge>
}

/**
 * Aktionen einer Zeile — eigene Komponente, weil ihre Mutationen die Kennung des Entwurfs brauchen.
 * Anders als in den Grunddaten stehen sie offen statt in einem Menü: es sind drei, sie gehören zum
 * täglichen Umgang mit Varianten, und ein Klappmenü verstecken hieße hier einen Klick mehr für alles.
 */
function DraftRowActions({ draft }: { draft: DraftSummaryDto }) {
  const patch = usePatchDraft(draft.id)
  const remove = useDeleteDraft(draft.id)
  const [rename, setRename] = useState(0)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const setFinished = async (finished: boolean) => {
    try {
      await patch.mutateAsync({ finished })
      toast.success(finished ? `„${draft.name}" liegt in der Ablage` : `„${draft.name}" ist wieder offen`)
    } catch { /* Toast kam aus dem Standard-onError */ }
  }

  return (
    <div className="flex items-center justify-end gap-0.5" onClick={(e) => e.stopPropagation()}>
      <Button variant="ghost" size="icon-sm" aria-label={`„${draft.name}" umbenennen`} title="Umbenennen" onClick={() => setRename((k) => k + 1)}>
        <Pencil />
      </Button>
      {draft.finished ? (
        <Button variant="ghost" size="icon-sm" aria-label={`„${draft.name}" wieder öffnen`} title="Wieder öffnen" onClick={() => void setFinished(false)} disabled={patch.isPending}>
          <ArchiveRestore />
        </Button>
      ) : (
        <Button variant="ghost" size="icon-sm" aria-label={`„${draft.name}" in die Ablage legen`} title="In die Ablage" onClick={() => void setFinished(true)} disabled={patch.isPending}>
          <Archive />
        </Button>
      )}
      <Button variant="ghost" size="icon-sm" aria-label={`„${draft.name}" verwerfen`} title="Verwerfen" onClick={() => setConfirmDelete(true)}>
        <Trash2 />
      </Button>
      {rename > 0 && <RenameDraftDialog key={rename} open onOpenChange={(o) => { if (!o) setRename(0) }} draftId={draft.id} currentName={draft.name} />}
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={`Entwurf „${draft.name}" verwerfen?`}
        description="Alle Stunden dieses Entwurfs gehen verloren. Der aktive Plan und die übrigen Entwürfe bleiben unverändert."
        confirmLabel="Verwerfen"
        destructive
        onConfirm={async () => {
          try {
            await remove.mutateAsync()
            toast.success(`Entwurf „${draft.name}" verworfen`)
          } catch { /* Toast kam */ }
        }}
      />
    </div>
  )
}

export function DraftListPage() {
  const drafts = useDrafts()
  const navigate = useNavigate()
  const { params, set } = useUrlState()
  const [newOpen, setNewOpen] = useState(0)

  // „Neuen Entwurf anlegen" aus der Befehlspalette (⌘K) führt nach /entwurf?aktion=anlegen. Der Dialog
  // steht offen, solange der Parameter da ist — abgeleitet statt in einem Effekt gesetzt; beim Schließen
  // wird der Parameter gestrichen, sonst risse ihn jedes erneute Öffnen der Seite wieder auf.
  const anlegen = params.get('aktion') === 'anlegen'

  if (drafts.isPending) return <PageSkeleton />
  if (drafts.isError) return <ErrorState problem={drafts.error} retry={() => void drafts.refetch()} />

  const rows = drafts.data
  const inArbeit = rows.filter((d) => !d.finished).length

  return (
    <>
      <PageHeader
        title="Entwürfe"
        description={rows.length === 0 ? 'Arbeitskopien neben dem aktiven Plan.' : `${rows.length === 1 ? '1 Entwurf' : `${rows.length} Entwürfe`} · ${inArbeit} in Arbeit · ${rows.length - inArbeit} in der Ablage`}
        actions={<Button onClick={() => setNewOpen((k) => k + 1)}><Plus /> Neuer Entwurf</Button>}
      />

      <DataTable<DraftSummaryDto>
        rows={rows}
        rowKey={(d) => d.id}
        onRowClick={(d) => navigate(`/entwurf/${d.id}`)}
        columns={[
          {
            key: 'name', header: 'Name',
            cell: (d) => (
              <div className="flex items-center gap-2.5">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface-sunken text-foreground-subtle">
                  {d.source === 'COPY_ACTIVE' ? <Copy className="size-3.5" aria-hidden /> : <FilePlus className="size-3.5" aria-hidden />}
                </span>
                <span className={d.finished ? 'font-medium text-foreground-subtle' : 'font-medium'}>{d.name}</span>
              </div>
            ),
          },
          { key: 'createdAt', header: 'Angelegt', className: 'w-44 tabular-nums', cell: (d) => formatDateTime(d.createdAt) },
          { key: 'source', header: 'Quelle', className: 'w-52', cell: (d) => SOURCE_LABEL[d.source] },
          { key: 'status', header: 'Status', className: 'w-52', cell: (d) => <StatusBadge draft={d} /> },
          {
            key: 'conflicts', header: 'Harte Konflikte', className: 'w-36 tabular-nums',
            cell: (d) => <span className={d.hardConflicts > 0 ? 'font-semibold text-conflict' : 'text-foreground-subtle'}>{d.hardConflicts}</span>,
          },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-28 text-right',
            cell: (d) => <DraftRowActions draft={d} />,
          },
        ]}
        empty={
          <EmptyState
            icon={PencilRuler}
            title="Noch kein Entwurf"
            description="Ein Entwurf ist eine Arbeitskopie: leer beginnen und berechnen lassen, oder den aktiven Plan kopieren und anpassen. Mehrere Entwürfe dürfen nebeneinander stehen."
            action={<Button onClick={() => setNewOpen((k) => k + 1)}><Plus /> Ersten Entwurf anlegen</Button>}
          />
        }
      />

      <p className="mt-4 text-xs text-foreground-subtle">
        Angenommen wird ein Entwurf auf seiner eigenen Seite. Der <Link to="/plan" className="font-medium text-primary underline-offset-2 hover:underline">aktive Plan</Link> bleibt gültig, bis der neue seinen Zeitraum übernimmt.
      </p>

      {(newOpen > 0 || anlegen) && (
        <NewDraftDialog
          key={newOpen}
          open
          onOpenChange={(o) => { if (!o) { setNewOpen(0); if (anlegen) set({ aktion: null }) } }}
          onCreated={(draft) => navigate(`/entwurf/${draft.id}`)}
        />
      )}
    </>
  )
}
