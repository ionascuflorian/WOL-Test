import { peekWake } from "@/lib/relay"

export async function GET() {
  const request = await peekWake()
  return Response.json({ pending: request !== null, request })
}