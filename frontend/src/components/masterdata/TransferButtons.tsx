// Kompakte Aus-/Einspielknöpfe für die Kopfzeile der Grunddatenseiten. Die ausführliche Variante
// mit Erklärkarte steht in den Einstellungen (TransferCard); hier ist es dieselbe Mechanik in klein:
// Dropdown am Kopfzeilenrand, Datei-Dialog, Vorschau vor dem Ersetzen.
import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { AlertTriangle, Download, FileUp, HardDriveDownload } from 'lucide-react'
import type { MasterDataExport } from '@/api/types'
import { useExportMasterData, useImportMasterData } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel,
  DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { downloadMasterData, isDataExists, preview, readMasterDataFile } from './transfer-helpers'

export function TransferButtons() {
  const qc = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [datei, setDatei] = useState<{ name: string; doc: MasterDataExport } | null>(null)
  const exportMut = useExportMasterData()
  const importMut = useImportMasterData({ quiet: isDataExists })

  const exportieren = async () => {
    try {
      const doc = await exportMut.mutateAsync()
      downloadMasterData(doc)
      toast.success('Grunddaten exportiert', { description: preview(doc) })
    } catch {
      /* Toast kommt aus dem Standard-onError */
    }
  }

  const onFile = async (file: File) => {
    try {
      setDatei({ name: file.name, doc: await readMasterDataFile(file) })
    } catch (err) {
      toast.error('Datei nicht lesbar', { description: err instanceof Error ? err.message : undefined })
    }
  }

  const einspielen = async () => {
    if (!datei) return
    try {
      const res = await importMut.mutateAsync({ ...datei.doc, replace: true })
      void qc.invalidateQueries()
      toast.success('Grunddaten importiert', { description: preview(res) })
      setDatei(null)
    } catch {
      /* Fehler-Toast kommt aus dem Standard-onError; der Dialog bleibt offen. */
    }
  }

  return (
    <>
      <input
        ref={fileInput}
        type="file"
        accept="application/json,.json"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0]
          // Der File-Input erlaubt es nicht, denselben File zweimal zu waehlen, ohne den value
          // zurueckzusetzen — sonst feuert `change` beim zweiten Mal nicht.
          e.target.value = ''
          if (f) void onFile(f)
        }}
      />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm" disabled={exportMut.isPending || importMut.isPending}>
            <HardDriveDownload data-icon="inline-start" /> Sichern
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-72">
          <DropdownMenuLabel className="text-xs font-normal text-foreground-subtle">
            Sicherung der Grunddaten — immer der ganze Bestand
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => void exportieren()}>
            <Download /> Grunddaten exportieren
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => fileInput.current?.click()}>
            <FileUp /> Grunddaten importieren…
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <ConfirmDialog
        open={datei != null}
        onOpenChange={(o) => { if (!o) setDatei(null) }}
        title={`„${datei?.name ?? ''}" importieren?`}
        description={
          datei ? (
            <div className="space-y-2 text-sm">
              <p>Die Datei enthält: {preview(datei.doc)}.</p>
              <p className="flex items-start gap-2 rounded-md bg-warning-soft px-3 py-2 text-warning">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
                <span><b>Vorhandene Grunddaten werden ersetzt.</b> Pläne, Entwürfe, Vertretungen, Ereignisse und der Verlauf gehen verloren. Regelwerk und Ferienzeiträume kommen aus der Datei.</span>
              </p>
            </div>
          ) : undefined
        }
        confirmLabel="Ersetzen"
        destructive
        onConfirm={einspielen}
      />
    </>
  )
}