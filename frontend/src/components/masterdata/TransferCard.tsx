// Karte „Grunddaten sichern" (Format-Version 1): Export als Datei, Import mit Vorschau.
//
// Anders als „Beispieldaten" (POST /api/sample-data, eingebauter Bestand) geht es hier um
// eine Datei, die die Planung herunterlaedt, aufbewahrt und spaeter wieder einspielt — auf
// derselben oder einer anderen Instanz. Der Import ersetzt immer den ganzen Bestand; die
// Rueckfrage vorher zeigt, was in der Datei steht.
import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { AlertTriangle, Download, FileUp, HardDriveDownload } from 'lucide-react'
import type { MasterDataExport, MasterDataImportResult } from '@/api/types'
import { useExportMasterData, useImportMasterData } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { SectionCard } from './shared'
import { downloadMasterData, isDataExists, preview, readMasterDataFile } from './transfer-helpers'

export function TransferCard() {
  const qc = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [datei, setDatei] = useState<{ name: string; doc: MasterDataExport } | null>(null)
  const exportMut = useExportMasterData()
  const importMut = useImportMasterData({ quiet: isDataExists })

  const download = async () => {
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
      const res: MasterDataImportResult = await importMut.mutateAsync({ ...datei.doc, replace: true })
      void qc.invalidateQueries()
      toast.success('Grunddaten importiert', { description: preview(res) })
      setDatei(null)
    } catch {
      // Fehler-Toast kommt aus dem Standard-onError; der Dialog bleibt offen.
    }
  }

  return (
    <SectionCard
      title="Grunddaten sichern"
      description="Alle Grunddaten (Fächer, Lehrkräfte, Räume, Klassen, Stundenraster, Regelwerk, Ferienzeiträume) als eine JSON-Datei. Systemkonfiguration und Benutzerkonten sind nicht enthalten."
    >
      <div className="grid gap-4 md:grid-cols-2">
        <div className="flex items-start gap-3 rounded-md border bg-surface px-3 py-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-primary-soft-foreground">
            <HardDriveDownload className="size-5" aria-hidden />
          </span>
          <div className="min-w-0">
            <div className="font-medium">Exportieren</div>
            <p className="mt-0.5 text-xs text-foreground-subtle">
              Lädt eine Datei <code className="font-mono">grunddaten-JJJJ-MM-TT.json</code> herunter. Sie lässt sich aufbewahren und auf einer anderen Instanz importieren.
            </p>
            <Button size="sm" className="mt-2" onClick={() => void download()} disabled={exportMut.isPending}>
              <Download data-icon="inline-start" /> Grunddaten exportieren
            </Button>
          </div>
        </div>

        <div className="flex items-start gap-3 rounded-md border bg-surface px-3 py-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-warning-soft text-warning">
            <FileUp className="size-5" aria-hidden />
          </span>
          <div className="min-w-0">
            <div className="font-medium">Importieren</div>
            <p className="mt-0.5 text-xs text-foreground-subtle">
              Ersetzt den kompletten Bestand. Vorhandene Konten von Lehrkräften und Klassen gehen mit; das Konto der Planung bleibt.
            </p>
            <input
              ref={fileInput}
              type="file"
              accept="application/json,.json"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0]
                e.target.value = ''
                if (f) void onFile(f)
              }}
            />
            <Button size="sm" variant="outline" className="mt-2" onClick={() => fileInput.current?.click()} disabled={importMut.isPending}>
              <FileUp data-icon="inline-start" /> Datei wählen
            </Button>
          </div>
        </div>
      </div>

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
    </SectionCard>
  )
}