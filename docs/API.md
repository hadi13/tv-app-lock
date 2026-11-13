<div dir="rtl">

# واجهة لوحة الجوال (HTTP API)

السيرفر داخل الشاشة على المنفذ `8787`. كل الردود JSON إلا الصور. الطلبات `POST` جسمها JSON.

## الدخول

| الطلب | الوصف |
|---|---|
| `POST /api/login` `{"pin":"1234"}` | يرجّع كوكي `t` (سنة). ٥ أخطاء ← `429` لمدة دقيقة. |
| `GET /api/logout` | يمسح الكوكي. |

كل اللي تحت يحتاج الكوكي، وإلا يرجّع `401`.

## القراءة

| الطلب | الوصف |
|---|---|
| `GET /` | صفحة اللوحة (`assets/panel.html`). |
| `GET /api/state` | الحالة الكاملة (تحت). |
| `GET /api/events` | `{"events":[{id,ts,icon,text}]}` أحدث ٨٠. |
| `GET /api/usage` | `{"days":[{date:"yyyyMMdd",total,apps:[{pkg,label,min}]}]}` آخر ٧ أيام، اليوم أولاً. |
| `GET /shot.jpg` | لقطة الشاشة الحين. `503` مع نص السبب إذا ما تنصوّر. |
| `GET /icon?pkg=...` | أيقونة التطبيق PNG. |

### `/api/state`

```json
{
  "cur": {"pkg":"com.google.android.youtube.tv","label":"YouTube","lockable":true,
          "open":true,"remain":"باقي ساعة","left":"٤ دقايق",
          "live":false,"channel":"","program":""},
  "lockScreen": true, "lockPkg": "com.netflix.ninja", "lockLabel": "Netflix",
  "lockReason": "⏱ خلص وقت اليوم (ساعتين)",
  "apps": [{"pkg":"...","label":"...","locked":true,"limit":120,"used":95,
            "open":false,"remain":"","blocked":false}],
  "bed": {"on":true,"start":1260,"end":420,"active":false},
  "ntfy": {"on":false,"topic":"tvlock-ab12cd34ef"},
  "lastEvent": 12, "serviceOn": true
}
```

## التحكم

| الطلب | الوصف |
|---|---|
| `POST /api/action` `{"a":"..."}` | `lockcurrent`، `lockall`، `home`، `back`، `open15`، `open1`، `openonce` (الثلاثة الأخيرة تفتح التطبيق اللي عليه شاشة القفل الحين). |
| `POST /api/app` | `{"pkg":"...", "locked":true, "limit":60, "open":60}`. كل حقل اختياري. `open`: دقايق، `-1` مفتوح دائماً، `0` اقفله الحين. |
| `POST /api/lockset` | `{"all":true}` اقفل الكل بالرقم، أو `{"none":true}` شيل القفل عن الكل. |
| `POST /api/bed` | `{"on":true,"start":1260,"end":420}` بالدقايق من منتصف الليل. |
| `POST /api/ntfy` | `{"on":true,"topic":"my-topic","test":true}` |
| `POST /api/pin` | `{"old":"1234","new":"5678"}` يرجّع كوكي جديد. |

## التشخيص (من داخل الشاشة بس)

يفتح فقط إذا الطلب جاي من `127.0.0.1` (يعني عن طريق `adb forward tcp:18787 tcp:8787` من الكمبيوتر):

| الطلب | الوصف |
|---|---|
| `GET /debug/info` | معلومات التصوير، التطبيق الحالي، القناة، والنصوص الخام من شاشة القنوات. |
| `GET /debug/shot.jpg` | لقطة بدون رقم سري. |

</div>
