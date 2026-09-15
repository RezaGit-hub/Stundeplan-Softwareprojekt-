// @vitest-environment jsdom
//
// Der Raumdialog mit Sperrzeiten (#133, T2-3.3): dieselbe Rastermaske wie bei der Lehrkraft, nur mit zwei
// Zuständen. Geprüft wird der Weg, der zählt: ein Klick ins Raster landet als `blockedTimeslotIds` im
// Request — beim Anlegen und beim Ändern — und ein zweiter Klick nimmt die Sperre wieder heraus.
import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage, testStore } from '@/test-utils'
import { RoomDialog } from './RoomDialog'

describe('RoomDialog (Sperrzeiten, #133)', () => {
  it('neuer Raum: ein Klick ins Raster wird als blockedTimeslotIds gespeichert', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<RoomDialog open onOpenChange={() => {}} />, { store })

    await nutzer.type(await screen.findByLabelText('Name'), 'Aula')
    // Raster ist da, sobald das Stundenraster geladen ist.
    const zelle = await screen.findByRole('gridcell', { name: 'Mo 1. Stunde: keine Angabe' })
    await nutzer.click(zelle)
    expect(screen.getByRole('gridcell', { name: 'Mo 1. Stunde: gesperrt' })).toBeTruthy()
    // Räume kennen kein „bevorzugt": der zweite Klick hebt die Sperre auf.
    await nutzer.click(screen.getByRole('gridcell', { name: 'Mo 1. Stunde: gesperrt' }))
    expect(screen.getByRole('gridcell', { name: 'Mo 1. Stunde: keine Angabe' })).toBeTruthy()
    await nutzer.click(screen.getByRole('gridcell', { name: 'Mo 1. Stunde: keine Angabe' }))

    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }))

    const mo1 = store.timeslots.find((t) => t.day === 'MO' && t.position === 1)!.id
    await waitFor(() => expect(store.rooms.find((r) => r.name === 'Aula')).toBeTruthy())
    expect(store.rooms.find((r) => r.name === 'Aula')!.blockedTimeslotIds).toEqual([mo1])
  })

  it('bestehender Raum: vorhandene Sperrzeiten stehen im Raster und werden beim Speichern mitgeschickt', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    const fr7 = store.timeslots.find((t) => t.day === 'FR' && t.position === 7)!.id
    const r101 = { ...store.rooms[0], blockedTimeslotIds: [fr7] }
    store.rooms[0].blockedTimeslotIds = [fr7]
    await renderPage(<RoomDialog open room={r101} onOpenChange={() => {}} />, { store })

    expect(await screen.findByRole('gridcell', { name: 'Fr 6. Stunde: gesperrt' })).toBeTruthy()
    await nutzer.click(screen.getByRole('gridcell', { name: 'Di 2. Stunde: keine Angabe' }))
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }))

    const di2 = store.timeslots.find((t) => t.day === 'TU' && t.position === 2)!.id
    await waitFor(() => expect(store.rooms[0].blockedTimeslotIds).toEqual([fr7, di2]))
  })
})
