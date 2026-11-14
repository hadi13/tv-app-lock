<p><b>English</b> · <a href="API.ar.md">العربية</a></p>

# Phone panel HTTP API

The server runs inside the TV on port `8787`. All responses are JSON except images. `POST` bodies are JSON.

## Sign in

| Request | Description |
|---|---|
| `POST /api/login` `{"pin":"1234"}` | Returns cookie `t` (1 year). 5 failures → `429` for a minute. |
| `GET /api/logout` | Clears the cookie. |

Everything below needs the cookie, otherwise `401`.

## Read

| Request | Description |
|---|---|
| `GET /` | The panel page (`assets/panel.html`). |
| `GET /api/state` | Full state (below). |
| `GET /api/events` | `{"events":[{id,ts,icon,text}]}` newest 80. |
| `GET /api/usage` | `{"days":[{date:"yyyyMMdd",total,apps:[{pkg,label,min}]}]}` last 7 days, today first. |
| `GET /shot.jpg` | Current screenshot. `503` with a text reason if it can't be captured. |
| `GET /icon?pkg=...` | App icon PNG. |

### `/api/state`

```json
{
  "cur": {"pkg":"com.google.android.youtube.tv","label":"YouTube","lockable":true,
          "open":true,"remain":"باقي ساعة","left":"٤ دقايق",
          "live":false,"channel":"","program":""},
  "lockScreen": true, "lockPkg": "com.netflix.ninja", "lockLabel": "Netflix",
  "lockReason": "⏱ خلص وقت اليوم (ساعتين)",
  "apps": [{"pkg":"...","label":"...","locked":true,"limit":120,"used":95,
            "open":false,"remain":"","blocked":false,"hidden":false}],
  "bed": {"on":true,"start":1260,"end":420,"active":false},
  "ntfy": {"on":false,"topic":"tvlock-ab12cd34ef"},
  "lastEvent": 12, "serviceOn": true
}
```

Human-readable strings (`remain`, `left`, `lockReason`) are in Arabic.

## Control

| Request | Description |
|---|---|
| `POST /api/action` `{"a":"..."}` | `lockcurrent`, `lockall`, `home`, `back`, `open15`, `open1`, `openonce` (the last three open the app currently on the lock screen). |
| `POST /api/app` | `{"pkg":"...", "locked":true, "limit":60, "hidden":false, "open":60}`, every field optional. `open`: minutes, `-1` always open, `0` lock now. |
| `POST /api/lockset` | `{"all":true}` PIN-lock everything, or `{"none":true}` remove all PIN locks. |
| `POST /api/bed` | `{"on":true,"start":1260,"end":420}` minutes after midnight. |
| `POST /api/ntfy` | `{"on":true,"topic":"my-topic","test":true}` |
| `POST /api/pin` | `{"old":"1234","new":"5678"}` returns a new cookie. |

## Diagnostics (from the TV only)

Only answered when the request comes from `127.0.0.1` (i.e. through `adb forward tcp:18787 tcp:8787` from your computer):

| Request | Description |
|---|---|
| `GET /debug/info` | Capture info, current app, channel, and raw text from the channels screen. |
| `GET /debug/shot.jpg` | Screenshot without the PIN. |
