# WA Assistant

Android app (Kotlin, min SDK 26) that drafts WhatsApp Business replies with Claude.
You approve every message. Nothing is ever sent automatically.

## How it works

1. A `NotificationListenerService` reads incoming **WhatsApp Business** notifications.
2. For each new customer message, Claude writes a short, friendly draft in the customer's language.
3. A pop-up notification shows the customer's message and the draft, with buttons:
   - **Send** – sends the draft.
   - **My reply** – type your own reply in any language; it's translated into the customer's language and shown for approval (tap **Send** again).
   - **Translate** – shows the customer's message and the draft in your language.
   - Tap the pop-up itself to open an editor (edit the draft, translate, send).
4. Replies are sent through WhatsApp's own notification **Reply** action, so they look like normal messages typed by you.

No WhatsApp Cloud API, no number migration, no unofficial APIs or modified apps.

## Download the APK

GitHub → **Actions** → latest **Build debug APK** run → **Artifacts** → `wa-assistant-debug-apk` (zip with the `.apk`).

## Setup on the phone

1. Install the APK (allow "install unknown apps").
2. Open WA Assistant:
   - Allow notification access → enable **WA Assistant**.
   - Allow pop-up notifications.
   - Disable battery optimization (important on Xiaomi, Huawei, Oppo, Samsung).
   - Paste your Claude API key (console.anthropic.com).
   - Set your language and business notes (prices, delivery, hours). The AI only uses facts written there.
3. Tap **Test** to see a sample pop-up.

## Limitations (read this)

- Works only when WhatsApp shows a notification. If the chat is open on the phone, or notifications for that chat are muted, nothing is drafted.
- The AI sees only what's in the notification plus replies sent from this app (no full chat history). Images, voice notes and documents arrive as "📷 Photo" etc. and can't be read.
- If the app process is killed, WhatsApp's reply action is lost for older pop-ups; reply in WhatsApp for those.
- Customer messages are sent to Anthropic's API to create drafts. The API key is stored in the app's private storage.
- Default model is Claude Opus 5.5 at low effort. Sonnet 5.5 / Haiku 5.5 can be chosen in settings for faster or cheaper replies.

## Build locally

```bash
./gradlew assembleDebug
```
