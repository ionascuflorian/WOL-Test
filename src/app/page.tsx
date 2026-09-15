"use client"

import { useCallback, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Loader2, MonitorUp, CheckCircle2, Radio } from "lucide-react"

type WakeRequest = {
  id: string
  requestedAt: number
}

type PollState =
  | { phase: "loading" }
  | { phase: "idle" }
  | { phase: "pending"; request: WakeRequest }
  | { phase: "delivered" }

export default function Home() {
  const [state, setState] = useState<PollState>({ phase: "loading" })
  const [sending, setSending] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const res = await fetch("/api/poll", { cache: "no-store" })
      const data = await res.json()
      if (data.pending) {
        setState({ phase: "pending", request: data.request })
      } else {
        setState((prev) => (prev.phase === "pending" ? { phase: "delivered" } : { phase: "idle" }))
      }
    } catch {
      setState({ phase: "idle" })
    }
  }, [])

  useEffect(() => {
    const id = setInterval(() => void refresh(), 4000)
    const first = setTimeout(() => void refresh(), 0)
    return () => {
      clearInterval(id)
      clearTimeout(first)
    }
  }, [refresh])

  const wake = async () => {
    setSending(true)
    try {
      const res = await fetch("/api/wake", { method: "POST" })
      const data = await res.json()
      if (data.ok && data.request) {
        setState({ phase: "pending", request: data.request })
      }
    } finally {
      setSending(false)
    }
  }

  const phaseIndicator = {
    loading: <Badge variant="outline">Se verifică</Badge>,
    idle: <Badge variant="secondary">Așteptare</Badge>,
    pending: <Badge variant="destructive">Comandă în așteptare</Badge>,
    delivered: <Badge>Livrat către rețea</Badge>,
  } as const

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-6">
      <div className="w-full max-w-md space-y-6">
        <div className="space-y-2 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
            <MonitorUp className="h-6 w-6 text-foreground" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">Wake on LAN</h1>
          <p className="text-sm text-muted-foreground">
            Trimite o comandă de pornire către laptopul tău, de oriunde.
          </p>
        </div>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base">Status</CardTitle>
                <CardDescription>Ultima actualizare la câteva secunde</CardDescription>
              </div>
              {phaseIndicator[state.phase]}
            </div>
          </CardHeader>
          <CardContent className="space-y-3">
            {state.phase === "loading" && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                Se verifică starea...
              </div>
            )}
            {state.phase === "idle" && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Radio className="h-4 w-4" />
                Nicio comandă activă. Aparatul Android este în alertă.
              </div>
            )}
            {state.phase === "pending" && (
              <div className="space-y-1.5 text-sm">
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Radio className="h-4 w-4 animate-pulse text-destructive" />
                  Comanda a fost trimisă și așteaptă preluare...
                </div>
                <div className="rounded-md bg-muted px-3 py-2 font-mono text-xs">
                  ID: {state.request.id.slice(0, 8)} · {new Date(state.request.requestedAt).toLocaleTimeString()}
                </div>
              </div>
            )}
            {state.phase === "delivered" && (
              <div className="flex items-center gap-2 text-sm text-foreground">
                <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                Comanda a fost preluată de dispozitivul Android și trimisă pe rețea.
              </div>
            )}
          </CardContent>
        </Card>

        <Button className="w-full h-14 text-lg gap-2" size="lg" onClick={wake} disabled={sending}>
          {sending ? <Loader2 className="h-5 w-5 animate-spin" /> : <MonitorUp className="h-5 w-5" />}
          Wake Up Laptop
        </Button>

        <p className="text-center text-xs text-muted-foreground">
          Comanda expiră în 3 minute dacă Android-ul nu este online.
        </p>
      </div>
    </div>
  )
}