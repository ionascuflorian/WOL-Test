import { queueWake } from "@/lib/relay"

export async function POST() {
  const request = await queueWake()
  return Response.json({ ok: true, request })
}