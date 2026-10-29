package com.example.myvpn

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.DataInputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** يفتح اتصال SSH ويشغّل خادم SOCKS5 محلي يمرر كل الاتصالات عبره (مثل ssh -D). */
class SshSocksTunnel {
    private var session: Session? = null
    private var server: ServerSocket? = null
    @Volatile private var running = false

    val isRunning get() = running && session?.isConnected == true

    fun start(sv: Server, localPort: Int, keepAliveSec: Int) {
        stop()
        AppLog.add("الاتصال بـ SSH: ${sv.host}:${sv.port}")
        val s = JSch().getSession(sv.user, sv.host, sv.port)
        s.setPassword(sv.password)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setServerAliveInterval(keepAliveSec * 1000)
        if (sv.payload.isNotBlank() || sv.tls || sv.connectHost.isNotBlank()) {
            val cPort = if (sv.connectPort > 0) sv.connectPort else if (sv.tls) 443 else 80
            val cHost = sv.connectHost.ifBlank { sv.host }
            AppLog.add("عبر الوسيط $cHost:$cPort" + if (sv.tls) " (TLS)" else "")
            s.setProxy(PayloadProxy(cHost, cPort, sv.tls, sv.sni, sv.payload))
        }
        s.connect(20000)
        session = s
        AppLog.add("تمت مصادقة SSH")

        val ss = ServerSocket(localPort, 50, InetAddress.getByName("127.0.0.1"))
        server = ss
        running = true
        AppLog.add("SOCKS5 يعمل على 127.0.0.1:$localPort")
        thread(isDaemon = true) {
            while (running) {
                val client = try { ss.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { handle(client) }
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        server = null
        session = null
    }

    private fun handle(client: Socket) {
        try {
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            if (input.read() != 5) return
            val methods = input.read()
            input.skipBytes(methods)
            output.write(byteArrayOf(5, 0)); output.flush()

            input.read()
            val cmd = input.read()
            input.read()
            val atyp = input.read()
            val host = when (atyp) {
                1 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) }).hostAddress
                3 -> String(ByteArray(input.read()).also { input.readFully(it) })
                4 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                else -> null
            }
            val port = (input.read() shl 8) or input.read()
            if (host == null || (cmd != 1 && cmd != 3)) {
                output.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)); return
            }
            if (cmd == 3) { udpAssociate(input, output); return }
            AppLog.add("→ $host:$port")

            val ch = session!!.openChannel("direct-tcpip") as ChannelDirectTCPIP
            ch.setHost(host)
            ch.setPort(port)
            ch.setInputStream(input)
            ch.setOutputStream(output)
            try {
                ch.connect(10000)
            } catch (e: Exception) {
                AppLog.add("✖ $host:$port — ${e.message}")
                output.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)); return
            }
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush()

            while (!ch.isClosed && !client.isClosed && running) Thread.sleep(200)
            ch.disconnect()
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    /** SOCKS5 UDP ASSOCIATE: يدعم استعلامات DNS فقط (تُرسل عبر SSH بصيغة TCP). */
    private fun udpAssociate(input: DataInputStream, output: OutputStream) {
        val udp = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        try {
            val p = udp.localPort
            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, (p shr 8).toByte(), (p and 0xFF).toByte()))
            output.flush()
            thread(isDaemon = true) { udpLoop(udp) }
            while (running && input.read() >= 0) { /* اتصال التحكم مفتوح */ }
        } finally {
            udp.close()
        }
    }

    private fun udpLoop(udp: DatagramSocket) {
        val buf = ByteArray(65535)
        while (running && !udp.isClosed) {
            val pkt = DatagramPacket(buf, buf.size)
            try { udp.receive(pkt) } catch (e: Exception) { break }
            val from = InetSocketAddress(pkt.address, pkt.port)
            val data = buf.copyOfRange(pkt.offset, pkt.offset + pkt.length)
            if (data.size < 10 || data[2].toInt() != 0) continue
            var i = 3
            val atyp = data[i++].toInt()
            val dstHost: String = when (atyp) {
                1 -> { val a = data.copyOfRange(i, i + 4); i += 4; InetAddress.getByAddress(a).hostAddress ?: continue }
                3 -> { val l = data[i++].toInt() and 0xFF; val s = String(data, i, l, Charsets.UTF_8); i += l; s }
                4 -> { val a = data.copyOfRange(i, i + 16); i += 16; InetAddress.getByAddress(a).hostAddress ?: continue }
                else -> continue
            }
            val dstPort = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            if (dstPort != 53) continue
            val header = data.copyOfRange(0, i)
            val query = data.copyOfRange(i, data.size)
            thread(isDaemon = true) { dnsOverSsh(udp, from, header, dstHost, query) }
        }
    }

    private fun dnsOverSsh(udp: DatagramSocket, to: InetSocketAddress, header: ByteArray, host: String, query: ByteArray) {
        try {
            val ch = session!!.openChannel("direct-tcpip") as ChannelDirectTCPIP
            ch.setHost(host)
            ch.setPort(53)
            val ins = DataInputStream(ch.inputStream)
            val outs = ch.outputStream
            ch.connect(8000)
            outs.write(byteArrayOf((query.size shr 8).toByte(), (query.size and 0xFF).toByte()))
            outs.write(query)
            outs.flush()
            val lenB = ByteArray(2)
            ins.readFully(lenB)
            val len = ((lenB[0].toInt() and 0xFF) shl 8) or (lenB[1].toInt() and 0xFF)
            val resp = ByteArray(len)
            ins.readFully(resp)
            ch.disconnect()
            val out = header + resp
            udp.send(DatagramPacket(out, out.size, to))
        } catch (e: Exception) {
            AppLog.add("✖ DNS $host — ${e.message}")
        }
    }
}
