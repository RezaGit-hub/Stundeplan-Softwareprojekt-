// Drag-and-Drop im Entwurf (dnd-kit): Kachel ziehen, Zelle als Ziel — Drop ruft onMove, das Raster ändert sich erst mit
// der Serverantwort (kein optimistisches Update). PointerSensor mit 6 px Schwelle trennt Klick und Ziehen.
import type { ReactNode } from 'react'
import { DndContext, DragOverlay, PointerSensor, pointerWithin, useDroppable, useSensor, useSensors } from '@dnd-kit/core'
import { GridCell, type GridCellProps } from './GridCell'

const announcements = {
  onDragStart: ({ active }: { active: { id: string | number } }) => `Stunde ${active.id} aufgenommen.`,
  onDragOver: ({ over }: { over: { id: string | number } | null }) => (over ? `Über Zeitfenster ${over.id}.` : 'Kein Zeitfenster unter der Kachel.'),
  onDragEnd: ({ over }: { over: { id: string | number } | null }) => (over ? `In Zeitfenster ${over.id} abgelegt.` : 'Verschieben abgebrochen.'),
  onDragCancel: () => 'Verschieben abgebrochen.',
}

export function GridDndContext({ onDragStart, onDragEnd, overlay, children }: {
  onDragStart(lessonId: number): void
  /** over = null: abgebrochen oder außerhalb abgelegt */
  onDragEnd(lessonId: number | null, timeslotId: number | null): void
  overlay: ReactNode
  children: ReactNode
}) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))
  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      accessibility={{ announcements, screenReaderInstructions: { draggable: 'Kachel mit der Maus ziehen; alternativ Kachel anklicken, „Verschieben" wählen und die Zielzelle anklicken.' } }}
      onDragStart={(e) => onDragStart(Number(e.active.id))}
      onDragCancel={() => onDragEnd(null, null)}
      onDragEnd={(e) => onDragEnd(Number(e.active.id), e.over ? Number(e.over.id) : null)}
    >
      {children}
      <DragOverlay dropAnimation={null} zIndex={40}>{overlay}</DragOverlay>
    </DndContext>
  )
}

/** Zelle als Drop-Ziel — jede Zelle inklusive Pausen (die Pausenregel meldet als Konflikt). Nur innerhalb von GridDndContext. */
export function DroppableCell({ id, ...cell }: GridCellProps & { id: number }) {
  const { setNodeRef, isOver } = useDroppable({ id })
  return <GridCell {...cell} dropRef={setNodeRef} isOver={isOver} />
}
