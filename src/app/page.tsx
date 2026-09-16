"use client"

import { useCallback, useEffect, useState } from "react"
import {
  Power,
  Loader2,
  Radio,
  CheckCircle2,
  Send,
  ArrowUpRight,
  Sun,
  Moon,
} from "lucide-react"

type WakeRequest = {
  id: string
  requestedAt: number
}

type Phase = "loading" | "idle" | "pending" | "delivered"

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString("ro-RO", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  })
}

export default function Home() {
  const [phase, setPhase] = useState<Phase>("loading")
  const [request, setRequest] = useState<WakeRequest | null>(null)
  const [sending, setSending] = useState(false)
  const [dark, setDark] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => {
      setDark(document.documentElement.classList.contains("dark"))
    }, 0)
    return () => clearTimeout(t)
  }, [])

  const toggleTheme = useCallback(() => {
    const next = !document.documentElement.classList.contains("dark")
    document.documentElement.classList.toggle("dark", next)
    setDark(next)
    try {
      localStorage.setItem("wol-theme", next ? "dark" : "light")
    } catch {
      /* storage indisponibil */
    }
  }, [])

  const refresh = useCallback(async () => {
    try {
      const res = await fetch("/api/poll", { cache: "no-store" })
      const data = await res.json()
      if (data.pending && data.request) {
        setRequest(data.request)
        setPhase("pending")
      } else {
        setRequest(null)
        setPhase((prev) => (prev === "pending" ? "delivered" : "idle"))
      }
    } catch {
      setPhase((prev) => (prev === "loading" ? "idle" : prev))
    }
  }, [])

  useEffect(() => {
    const id = setInterval(() => void refresh(), 4000)
    const first = setTimeout(() => void refresh(), 50)
    return () => {
      clearInterval(id)
      clearTimeout(first)
    }
  }, [refresh])

  const wake = async () => {
    if (sending) return
    setSending(true)
    try {
      const res = await fetch("/api/wake", { method: "POST" })
      const data = await res.json()
      if (data.ok && data.request) {
        setRequest(data.request)
        setPhase("pending")
      }
    } finally {
      setSending(false)
    }
  }

  const heroLabel = sending
    ? "Se trimite comanda…"
    : "Pornește laptopul"

  return (
    <main className="relative min-h-screen overflow-hidden bg-white text-zinc-900 dark:bg-[#0a0e1a] dark:text-zinc-100">
      {/* aurora */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,#eef4ff_0%,#f6edff_50%,#e9fcf4_100%)] dark:bg-[linear-gradient(180deg,#0d1226_0%,#101022_50%,#0b1420_100%)]"
      >
        <div className="animate-aurora-a absolute -top-[15%] -left-[10%] h-[55vmax] w-[55vmax] rounded-full bg-[#7db9ff]/45 blur-3xl dark:bg-[#1d4ed8]/30" />
        <div className="animate-aurora-b absolute top-[5%] -right-[15%] h-[50vmax] w-[50vmax] rounded-full bg-[#c9a0ff]/40 blur-3xl dark:bg-[#6d28d9]/25" />
        <div className="animate-aurora-c absolute -bottom-[20%] left-[15%] h-[50vmax] w-[50vmax] rounded-full bg-[#8bf0cf]/40 blur-3xl dark:bg-[#0f766e]/25" />
      </div>

      <div className="relative z-10 flex min-h-screen flex-col items-center justify-center px-5 py-12">
        {/* theme toggle */}
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={dark ? "Activare mod luminos" : "Activare mod întunecat"}
          className="absolute top-5 right-5 flex h-11 w-11 items-center justify-center rounded-full bg-white/60 shadow-sm ring-1 ring-white/70 backdrop-blur transition-transform duration-200 hover:scale-105 active:scale-95 dark:bg-white/10 dark:ring-white/15
            focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF]/70 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-[#0A84FF]/80 dark:focus-visible:ring-offset-[#0a0e1a]"
        >
          {dark ? (
            <Sun className="h-5 w-5 text-amber-300" />
          ) : (
            <Moon className="h-5 w-5 text-[#007AFF]" />
          )}
        </button>

        <div className="animate-rise-in w-full max-w-[380px]">
          {/* app header */}
          <div className="mb-6 flex items-center justify-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-[11px] bg-white/60 shadow-sm ring-1 ring-white/70 backdrop-blur dark:bg-white/10 dark:ring-white/15">
              <Power className="h-[18px] w-[18px] text-[#007AFF] dark:text-[#0A84FF]" />
            </div>
            <span className="text-[17px] font-semibold tracking-tight text-zinc-900/90 dark:text-zinc-100/90">
              WOL Remote
            </span>
          </div>

          {/* glass panel */}
          <section className="glass-panel px-7 pb-7 pt-8" aria-label="Control Wake on LAN">
            <div className="mb-7 space-y-1.5 text-center">
              <h1 className="text-[24px] font-semibold tracking-tight">
                Wake on LAN
              </h1>
              <p className="text-[14px] leading-relaxed text-zinc-500 dark:text-zinc-400">
                Apelează butonul și laptopul pornește în câteva secunde.
              </p>
            </div>

            {/* hero button */}
            <button
              type="button"
              onClick={wake}
              disabled={sending}
              className={[
                "group relative flex h-[64px] w-full items-center justify-center gap-2.5 rounded-full text-[17px] font-semibold tracking-tight",
                "transition-[transform,box-shadow] duration-200 ease-out active:scale-[0.975]",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF]/70 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-[#0A84FF]/80 dark:focus-visible:ring-offset-[#141831]",
                sending
                  ? "bg-[#007AFF]/90 text-white shadow-[0_10px_30px_-8px_rgba(0,122,255,0.55)] dark:bg-[#0A84FF]/85"
                  : "bg-white/80 text-[#007AFF] shadow-[0_10px_30px_-8px_rgba(0,122,255,0.45),inset_0_1px_0_rgba(255,255,255,0.95)] ring-1 ring-white/80 backdrop-blur hover:bg-white/95 dark:bg-white/12 dark:text-[#0A84FF] dark:hover:bg-white/18 dark:ring-white/15",
                phase === "delivered" && !sending &&
                  "bg-[#34C759]/12 text-[#28a745] dark:bg-[#34C759]/15 dark:text-[#34C759]",
              ].join(" ")}
            >
              {sending ? (
                <Loader2 className="h-5 w-5 animate-spin" />
              ) : phase === "delivered" ? (
                <CheckCircle2 className="h-5 w-5" />
              ) : (
                <Power className="h-5 w-5 transition-transform duration-200 group-hover:scale-110" />
              )}
              {heroLabel}
            </button>

            <p className="mt-2.5 text-center text-[12.5px] text-zinc-400 dark:text-zinc-500">
              Comanda expiră în 3 minute dacă dispozitivul nu e online.
            </p>

            {/* status row */}
            <div
              aria-live="polite"
              className="glass-inset mt-6 flex items-center gap-3 px-4 py-3.5"
            >
              <StatusIcon phase={phase} />
              <div className="min-w-0 flex-1">
                <p className="text-[13.5px] font-medium leading-snug text-zinc-800 dark:text-zinc-200">
                  <StatusText phase={phase} />
                </p>
                {phase === "pending" && request && (
                  <p className="mt-0.5 truncate font-mono text-[11px] text-zinc-400 dark:text-zinc-500">
                    {request.id.slice(0, 8)} · {formatTime(request.requestedAt)}
                  </p>
                )}
              </div>
              <StatusBadge phase={phase} />
            </div>
          </section>

          <p className="mt-6 flex items-center justify-center gap-1.5 text-center text-[12.5px] text-zinc-500/90 dark:text-zinc-400/80">
            Controlează din orice browser
            <ArrowUpRight className="h-3.5 w-3.5" />
          </p>
        </div>
      </div>
    </main>
  )
}

function StatusIcon({ phase }: { phase: Phase }) {
  const common = "h-[18px] w-[18px] shrink-0"
  switch (phase) {
    case "loading":
      return <Loader2 className={`${common} animate-spin text-zinc-400`} />
    case "pending":
      return <Send className={`${common} animate-pulse text-amber-500`} />
    case "delivered":
      return <CheckCircle2 className={`${common} text-[#34C759]`} />
    default:
      return <Radio className={`${common} text-zinc-400`} />
  }
}

function StatusText({ phase }: { phase: Phase }) {
  switch (phase) {
    case "loading":
      return "Se verifică starea dispozitivului…"
    case "pending":
      return "Comanda a fost trimisă și așteaptă dispozitivul Android."
    case "delivered":
      return "Pachetul de activare a fost trimis pe rețeaua locală."
    default:
      return "Dispozitivul Android e pregătit să primească comanda."
  }
}

function StatusBadge({ phase }: { phase: Phase }) {
  const styles: Record<Phase, string> = {
    loading: "bg-zinc-500/10 text-zinc-500",
    idle: "bg-zinc-500/10 text-zinc-500",
    pending: "bg-amber-500/15 text-amber-600 dark:text-amber-400",
    delivered: "bg-[#34C759]/15 text-[#28a745] dark:text-[#34C759]",
  }
  const labels: Record<Phase, string> = {
    loading: "…",
    idle: "Alerta",
    pending: "În așteptare",
    delivered: "Livrat",
  }
  return (
    <span className={`shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold ${styles[phase]}`}>
      {labels[phase]}
    </span>
  )
}