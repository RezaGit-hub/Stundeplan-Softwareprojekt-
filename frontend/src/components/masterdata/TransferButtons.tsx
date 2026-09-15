// Bereichs-Aus-/Einspielknöpfe für die Kopfzeile einer Grunddatenseite. Die Datei enthält **nur
// diesen Bereich** (Format-Version 2) — auf der Lehrkraftseite also nur Lehrkräfte. Fächer und
// Zeitfenster werden beim Import über Kurzname bzw. Tag+Position zugeordnet; stehen sie auf der
// Zielinstanz nicht, meldet der Server einen Feldfehler, und der Dialog bleibt offen.
import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Download, FileUp, HardDriveDownload, Info } from 'lucide-react'
import type { MasterDataSection, TransferScope } from '@/api/types'
import { useExportMasterDataSection, useImportMasterDataSection } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel,
  DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { downloadSection, previewSection, previewSectionResult, readSectionFile, SCOPE_LABEL } from './transfer-helpers'

const REFS_HINWEIS: Partial<Record<TransferScope, string>> = {
  teachers: 'Fächer und Zeitfenster werden über Kurzname bzw. Tag+Position zugeordnet — sie müssen hier vorhanden sein.',
  classes: 'Fächer und Lehrkräfte werden über Kürzel zugeordnet — sie müssen hier vorhanden sein.',
}

export function TransferButtons({ scope }: { scope: TransferScope }) {
  const qc = useQueryClient()
  const label = SCOPE_LABEL[scope]
  const fileInput = useRef<HTMLInputElement>(null)
  const [datei, setDatei] = useState<{ name: string; doc: MasterDataSection } | null>(null)
  const exportMut = useExportMasterDataSection(scope)
  const importMut = useImportMasterDataSection(scope)

  const exportieren = async () => {
    try {
      const doc = await exportMut.mutateAsync()
      downloadSection(doc)
      toast.success(`${label.plural} exportiert`, { description: previewSection(doc) })
    } catch { /* Toast aus dem Standard-onError */ }
  }

  const onFile = async (file: File) => {
    try {
      setDatei({ name: file.name, doc: await readSectionFile(file, scope) })
    } catch (err) {
      toast.error('Datei nicht lesbar', { description: err instanceof Error ? err.message : undefined })
    }
  }

  const einspielen = async () => {
    if (!datei) return
    try {
      const res = await importMut.mutateAsync(datei.doc)
      void qc.invalidateQueries()
      const beschreibung = previewSectionResult(res)
      toast.success(`${label.plural} importiert`, {
        description: res.warnings.length > 0 ? `${beschreibung} · ${res.warnings.join(' · ')}` : beschreibung,
      })
      setDatei(null)
    } catch { /* Fehler-Toast kommt aus dem Standard-onError; der Dialog bleibt offen. */ }
  }

  const hinweis = REFS_HINWEIS[scope]

  return (
    <>
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
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm" disabled={exportMut.isPending || importMut.isPending}>
            <HardDriveDownload data-icon="inline-start" /> {label.plural} sichern
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-80">
          <DropdownMenuLabel className="text-xs font-normal text-foreground-subtle">
            Nur {label.plural} — andere Bereiche bleiben unverändert
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => void exportieren()}>
            <Download /> {label.plural} exportieren
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => fileInput.current?.click()}>
            <FileUp /> {label.plural} importieren…
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem asChild>
            <a href="/einstellungen?bereich=transfer" className="text-xs text-foreground-subtle">
              <Info /> Ganze Schule sichern — Einstellungen → Sicherung
            </a>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <ConfirmDialog
        open={datei != null}
        onOpenChange={(o) => { if (!o) setDatei(null) }}
        title={`${label.plural} aus „${datei?.name ?? ''}" importieren?`}
        description={
          datei ? (
            <div className="space-y-2 text-sm">
              <p>Die Datei enthält: {previewSection(datei.doc)}.</p>
              <p className="text-foreground-muted">
                Vorhandene {label.plural} mit demselben Schlüssel werden aktualisiert, neue kommen dazu.
                Andere Bereiche (Fächer, Klassen, Räume, Planung) bleiben unverändert.
              </p>
              {hinweis && (
                <p className="rounded-md bg-surface-sunken px-3 py-2 text-xs text-foreground-muted">{hinweis}</p>
              )}
            </div>
          ) : undefined
        }
        confirmLabel="Einspielen"
        onConfirm={einspielen}
      />
    </>
  )
}