import { peekLast, peekWake } from "@/lib/relay"

export async function GET() {
  const request = await peekWake()
  const last = await peekLast()
  return Response.json({ pending: request !== null, request, last })
}