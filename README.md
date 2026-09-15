# WOL Remote — pornire laptop la distanță

Sistem complet pentru a porni laptopul de la distanță (Wake-on-LAN), prin intermediul unei
interfețe web și a unui telefon Android (vechi, Android 9) aflat pe **același WiFi** ca
laptopul.

## Cum funcționează

```
[Browser, oriunde]       [Vercel]                 [Upstash Redis]         [Android 9, același WiFi ca laptopul]
        │  POST /api/wake      │                          │                              │
        ├──────────────────────►  queueWake() ──────────► SET wol:pending (TTL 180s)      │
        │                       │                          │                              │
        │                       │        la fiecare 5s     │   GET /api/poll              │
        │                       │◄─────────────────────────│                              │
        │                       │                          │                              ▼
        │                       │                          │   trimite magic packet UDP    ──► port 9 broadcast local
        │                       │◄──────────────────── POST /api/ack (după trimitere)     │
```

Punctul cheie: pachetul WOL (UDP broadcast) **nu traversează internetul**. De aceea avem nevoie
de telefonul Android pe rețeaua locală — el preia comanda de pe "relay-ul" cloud și emite
magic packet-ul local.

## Componente

- **Web UI** — pagină cu buton „Wake Up Laptop" + status live. Hostată gratis pe Vercel.
- **Relay API** — 3 endpoint-uri care țin „coada" de comenzi în Upstash Redis (free tier).
  - `POST /api/wake` — pune o comandă de wake în coadă (TTL 3 minute)
  - `GET  /api/poll` — Android-ul verifică dacă există o comandă activă
  - `POST /api/ack` — Android-ul confirmă că a emis pachetul WOL
- **Android app** — aplicație Kotlin nativ (minSdk 28 = Android 9), fără dependențe externe.

## 1. Hostare relay (web) — gratuit

### Upstash Redis
Creează un database gratuit pe https://console.upstash.com/redis și copiază din tab-ul
**REST API** cele două valori (`UPSTASH_REDIS_REST_URL`, `UPSTASH_REDIS_REST_TOKEN`).

### Vercel
1. Pune fișierele în git și importează repo-ul pe https://vercel.com (sau `npx vercel`)
2. Adaugă cele două variabile de mediu în **Settings → Environment Variables**:
   - `UPSTASH_REDIS_REST_URL`
   - `UPSTASH_REDIS_REST_TOKEN`
3. Deploy. Vei obține un URL `https://numele-app.vercel.app`

> Test local: copiază `.env.example` în `.env.local` și completează valorile — sau rulezi
> in-memory (comenzile se pierd la restart).

## 2. Pregătirea laptopului (WOL)

- În BIOS: activează **Wake on LAN / Power On by PCI-E**
- În Windows: Device Manager → **Network adapter** → Properties → **Power Management**
  → bifează „Allow this device to wake the computer"
- Găsește MAC-ul: `getmac` în Command Prompt (linia corespunzătoare adaptorului cu care e conectat)

## 3. Aplicația Android

APK-ul se află la:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

Instalare:
1. Copiază `app-debug.apk` pe telefon
2. Permite instalarea din surse necunoscute (setări → securitate)
3. Instalează și deschide **WOL Remote**
4. Configurează:
   - **Server (relay):** `https://numele-app.vercel.app`
   - **MAC laptop:** `AA:BB:CC:DD:EE:FF`
   - **Broadcast:** lasă gol (detectat automat) sau pune ex. `192.168.1.255`
   - **Interval:** 5 secunde
5. Apasă **PORNEȘTE serviciul** (rămâne activ în fundal cu notificare persistentă)

### Rebuild APK (dacă modifici ceva)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
cd android
.\gradlew.bat assembleDebug
```
Rezultat: `android\app\build\outputs\apk\debug\app-debug.apk`

## 4. Utilizare

1. Asigură-te că telefonul e pe același WiFi ca laptopul și serviciul rulează
2. Din orice browser: deschide URL-ul Vercel
3. Apasă **Wake Up Laptop**
4. Butonul devine „Comandă în așteptare" → în maxim intervalul setat pe Android,
   pachetul WOL ajunge la laptop și acesta pornește

## Structură proiect

```
├── src/
│   ├── app/
│   │   ├── page.tsx            # Web UI (Wake Up)
│   │   └── api/
│   │       ├── wake/route.ts   # POST — pune comandă în coadă
│   │       ├── poll/route.ts   # GET — verifică comanda activă
│   │       └── ack/route.ts    # POST — confirmă trimiterea
│   └── lib/relay.ts            # back-end relay (Upstash / in-memory)
└── android/
    └── app/src/main/
        ├── java/com/example/wolremote/
        │   ├── MainActivity.kt # settings + start/stop service
        │   ├── WakeService.kt  # foreground service (polling)
        │   ├── WakeOnLan.kt    # magic packet UDP
        │   ├── Http.kt         # client HTTP /api/poll /api/ack
        │   └── Prefs.kt        # SharedPreferences
        └── res/                # layout, strings, icon
```