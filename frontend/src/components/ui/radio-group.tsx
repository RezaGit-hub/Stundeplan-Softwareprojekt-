// Radiogruppe mit freiem Aussehen: Radix bringt die Tastatur mit (Pfeiltasten wechseln die Auswahl,
// die Gruppe ist genau einmal im Tab-Fokus), das Aussehen kommt vom Aufrufer. Angelegt 05.09. für die
// drei Gruppen, die role="radiogroup"/role="radio" von Hand setzten, ohne Pfeiltasten anzubieten —
// Screenreader kündigten damit eine Bedienung an, die es nicht gab (Befund FRONTEND-7 b).
//
// Bewusst ohne den runden Punkt der shadcn-Vorlage: die drei Stellen sind Segmentschalter und
// Farbfelder, nicht klassische Auswahlknöpfe.
import * as React from 'react'
import { RadioGroup as RadioGroupPrimitive } from 'radix-ui'
import { cn } from '@/lib/utils'

function RadioGroup({ className, ...props }: React.ComponentProps<typeof RadioGroupPrimitive.Root>) {
  return <RadioGroupPrimitive.Root data-slot="radio-group" className={cn(className)} {...props} />
}

function RadioGroupItem({ className, ...props }: React.ComponentProps<typeof RadioGroupPrimitive.Item>) {
  return (
    <RadioGroupPrimitive.Item
      data-slot="radio-group-item"
      className={cn(
        'transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-50',
        className,
      )}
      {...props}
    />
  )
}

export { RadioGroup, RadioGroupItem }
