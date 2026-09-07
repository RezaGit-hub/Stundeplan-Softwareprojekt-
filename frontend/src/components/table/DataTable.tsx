import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { listItem } from '@/lib/motion'
import { cn } from '@/lib/utils'

export interface DataTableColumn<T> {
  key: string
  header: ReactNode
  cell(row: T): ReactNode
  className?: string
}

/** Dünne Hülle um shadcn table: Spalten als Tabelle, Zeilen mit Eintritt-Animation. Keine Sortierung, kein Filter. */
export function DataTable<T>({ rows, columns, rowKey, onRowClick, empty, animate = true, className }: {
  rows: T[]
  columns: DataTableColumn<T>[]
  rowKey(row: T): string | number
  onRowClick?(row: T): void
  empty: ReactNode
  animate?: boolean
  className?: string
}) {
  if (rows.length === 0) return <>{empty}</>
  const Row = animate ? motion.tr : 'tr'
  return (
    <div className={cn('overflow-x-auto rounded-lg border bg-card', className)}>
      <Table>
        <TableHeader>
          <TableRow>
            {columns.map((c) => (
              <TableHead key={c.key} className={cn('text-xs', c.className)}>{c.header}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, i) => (
            <Row
              key={rowKey(row)}
              {...(animate ? listItem(i) : {})}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={cn('border-b transition-colors last:border-0 hover:bg-muted/50', onRowClick && 'cursor-pointer')}
            >
              {columns.map((c) => (
                <TableCell key={c.key} className={c.className}>{c.cell(row)}</TableCell>
              ))}
            </Row>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
