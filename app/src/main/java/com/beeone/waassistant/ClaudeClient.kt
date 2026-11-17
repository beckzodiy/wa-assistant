package com.beeone.waassistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import org.json.JSONObject
import java.time.Duration

data class DraftResult(
    val customerLanguage: String,
    val messageTranslation: String,
    val draft: String,
    val draftTranslation: String,
)

class ClaudeException(message: String) : Exception(message)

/** Talks to the Claude API. All functions block: call them from Dispatchers.IO. */
object ClaudeClient {
    private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

    @Volatile private var cached: Pair<String, AnthropicClient>? = null

    private fun client(apiKey: String): AnthropicClient {
        cached?.let { (k, c) -> if (k == apiKey) return c }
        val c = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(45))
            .maxRetries(2)
            .build()
        cached = apiKey to c
        return c
    }

    private fun systemPrompt(prefs: Prefs) = """
You write WhatsApp replies for the customer chat of Bee One Global, a company in the UAE that sells Mommy Knows baby care products (diapers, baby wipes, sanitary napkins). Your text is sent from the business's WhatsApp account and must read exactly like a message typed by a friendly human staff member.

Rules for every reply:
- Short: usually 1-2 sentences, never more than 3. WhatsApp style, warm and polite.
- Write in the same language and script the customer used in their latest message (Arabic, English, Russian, Uzbek, Hindi, Urdu, Tagalog, etc.). If they mix languages, follow their main one.
- Never mention or hint at AI, bots, assistants, automation, drafts, or translation. Never say "as an AI". You are the staff member.
- Plain text only: no markdown, no bullet points, no quotation marks around the reply, no signature, no sign-off with a name.
- At most one emoji, and only if it fits naturally.
- Never invent prices, stock levels, delivery times, discounts or policies. Use only the business notes below. If you don't know, say you'll check and get back to them shortly.
- Answer what they actually asked. For greetings, greet back and ask how you can help.

Business notes from the owner:
${prefs.businessNotes.trim()}
""".trim()

    private fun formatHistory(lines: List<ChatLine>): String =
        lines.takeLast(12).joinToString("\n") { (if (it.fromMe) "Us: " else "Customer: ") + it.text }

    /** Creates a reply draft plus translations for the owner, in one fast call. */
    fun draftReply(prefs: Prefs, chat: Chat): DraftResult {
        val myLang = prefs.myLanguage
        val user = """
Conversation so far (oldest first):
${formatHistory(chat.historySnapshot())}

Customer's latest message(s):
${chat.customerMessage}

Write the next reply from us. Respond with only a JSON object, no other text:
{"customer_language": "<language name in English>", "message_translation": "<customer's latest message(s) translated into $myLang>", "draft": "<the reply to send, in the customer's language>", "draft_translation": "<the reply translated into $myLang>"}
""".trim()

        val raw = call(prefs, systemPrompt(prefs), user)
        return parseDraft(raw)
    }

    /** Translates the owner's own text into the customer's language, polished but same meaning. */
    fun translateMyReply(prefs: Prefs, chat: Chat, myText: String): String {
        val lang = chat.customerLanguage.ifBlank { "the same language the customer writes in" }
        val user = """
Conversation so far (oldest first):
${formatHistory(chat.historySnapshot())}

The staff member wrote this reply (possibly in another language, possibly with typos):
$myText

Rewrite it in $lang so it can be sent to the customer. Keep the exact meaning and any numbers, prices or dates. Fix grammar and spelling, keep it short and natural. Do not add new facts. If it's already in $lang, just correct it lightly.
Output only the final message text, nothing else.
""".trim()
        return clean(call(prefs, systemPrompt(prefs), user))
    }

    /** Translates text into the owner's language (used when no cached translation exists). */
    fun translateForMe(prefs: Prefs, text: String): String {
        val user = "Translate this WhatsApp message into ${prefs.myLanguage}. Output only the translation.\n\n$text"
        return clean(call(prefs, "You are a precise translator.", user))
    }

    private fun call(prefs: Prefs, system: String, user: String): String {
        val key = prefs.apiKey
        if (key.isBlank()) throw ClaudeException("Add your Claude API key in WA Assistant settings")
        val model = prefs.model

        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .system(system)
            .addUserMessage(user)
            // Low effort = fastest replies; plenty for short chat messages.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
        if (!model.startsWith("claude-haiku")) {
            // If the main model declines a message, the API retries on a suitable model automatically.
            builder.addBeta(FALLBACK_BETA).fallbacksDefault()
        }

        val response = try {
            client(key).beta().messages().create(builder.build())
        } catch (e: UnauthorizedException) {
            throw ClaudeException("Invalid Claude API key")
        } catch (e: RateLimitException) {
            throw ClaudeException("Rate limited by Claude API, try again in a moment")
        } catch (e: AnthropicServiceException) {
            throw ClaudeException("Claude API error ${e.statusCode()}")
        } catch (e: Exception) {
            throw ClaudeException("Network error: ${e.message ?: e.javaClass.simpleName}")
        }

        if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
            throw ClaudeException("AI declined this message, please reply yourself")
        }
        val text = response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
        if (text.isEmpty()) throw ClaudeException("Empty AI response")
        return text
    }

    internal fun parseDraft(raw: String): DraftResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            try {
                val o = JSONObject(raw.substring(start, end + 1))
                val draft = clean(o.optString("draft"))
                if (draft.isNotEmpty()) {
                    return DraftResult(
                        customerLanguage = o.optString("customer_language").trim(),
                        messageTranslation = o.optString("message_translation").trim(),
                        draft = draft,
                        draftTranslation = o.optString("draft_translation").trim(),
                    )
                }
            } catch (_: Exception) { /* fall through */ }
        }
        // Model ignored the format: use the whole text as the draft.
        return DraftResult("", "", clean(raw), "")
    }

    /** Removes anything that would look machine-made in WhatsApp. */
    internal fun clean(s: String): String {
        var t = s.trim()
        if (t.length >= 2 && (t.first() == '"' && t.last() == '"' || t.first() == '“' && t.last() == '”')) {
            t = t.substring(1, t.length - 1).trim()
        }
        t = t.replace("**", "").replace(Regex("(?m)^#+\\s*"), "")
        return t
    }
}
