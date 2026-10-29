package com.example.myvpn

import java.net.URLDecoder

/**
 * يحوّل ملف الاشتراك (نص أو Base64) إلى سيرفرات.
 * كل سطر هو أحد الأنواع:
 *   vless://...   vmess://...   trojan://...        (سيرفرات V2Ray)
 *   ssh://user:pass@host:port?host=..&cport=..&tls=1&sni=..&payload=..#الاسم   (سيرفر SSH)
 * القيم الخاصة (كلمة المرور، الـ payload) تُكتب بترميز URL، مثل %20 للمسافة.
 */
object SubParser {

    fun parse(raw: String, subUrl: String): List<Server> {
        var body = raw.removePrefix("\uFEFF").trim()
        if (!body.contains("://")) {
            body = try { V2rayManager.b64(body) } catch (_: Exception) { body }
        }
        val result = ArrayList<Server>()
        val seen = HashSet<String>()
        body.lines().map { it.trim() }.forEach { line ->
            if (line.isEmpty() || !seen.add(line)) return@forEach
            val server = when {
                line.startsWith("ssh://", true) -> parseSsh(line, subUrl)
                V2rayManager.isSupportedLink(line) -> Server(
                    name = V2rayManager.nameOf(line).ifBlank { "سيرفر ${result.size + 1}" },
                    type = "V2RAY", config = line, sub = subUrl
                )
                else -> null
            }
            if (server != null) result.add(server)
        }
        return result
    }

    private fun dec(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    private fun parseSsh(line: String, sub: String): Server? = try {
        var s = line.substring(6)
        val name = if ('#' in s) dec(s.substringAfterLast('#')) else ""
        if ('#' in s) s = s.substringBeforeLast('#')
        val query = if ('?' in s) s.substringAfter('?') else ""
        s = s.substringBefore('?')

        val at = s.lastIndexOf('@')
        val userInfo = s.substring(0, at)
        val hostPort = s.substring(at + 1)
        val user = dec(userInfo.substringBefore(':'))
        val pass = dec(userInfo.substringAfter(':', ""))
        val host = if (':' in hostPort) hostPort.substringBeforeLast(':') else hostPort
        val port = if (':' in hostPort) hostPort.substringAfterLast(':').toIntOrNull() ?: 22 else 22

        val q = HashMap<String, String>()
        query.split('&').filter { it.contains('=') }.forEach {
            q[it.substringBefore('=')] = dec(it.substringAfter('='))
        }
        if (host.isBlank() || user.isBlank()) null else Server(
            name = name.ifBlank { "SSH $host" },
            type = "SSH", host = host, port = port, user = user, password = pass,
            connectHost = q["host"].orEmpty(),
            connectPort = q["cport"]?.toIntOrNull() ?: 0,
            tls = q["tls"] == "1" || q["tls"].equals("true", true),
            sni = q["sni"].orEmpty(),
            payload = q["payload"].orEmpty(),
            sub = sub
        )
    } catch (_: Exception) { null }
}
