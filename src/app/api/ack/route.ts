import { consumeWake, type WakeStatus } from "@/lib/relay"

export async function POST(request: Request) {
  let status: WakeStatus = "sent"
  try {
    const body = (await request.json()) as { status?: WakeStatus }
    if (body.status === "up" || body.status === "sent") {
      status = body.status
    }
  } catch {
    // body absent — păstrăm default-ul "sent"
  }
  await consumeWake(status)
  return Response.json({ ok: true })
}