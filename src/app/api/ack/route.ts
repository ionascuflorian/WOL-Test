import { consumeWake } from "@/lib/relay"

export async function POST() {
  await consumeWake()
  return Response.json({ ok: true })
}