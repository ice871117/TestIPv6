package com.tencent.testipv6

import android.util.Log
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.UnknownHostException

object IPEnvironment {

    const val TAG = "IPEnvironment"

    val REG_IPV6 = Regex("^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$")

    enum class NetType {
        UNKNOWN, IPV4_ONLY, IPV6_ONLY, IPV6_DUAL, IPV6_NAT64
    }

    internal enum class IPSupportType {
        NONE, IPV4, IPV6, IPV6_DUAL
    }

    suspend fun resolveDomain(domain: String, withFailMsg: Boolean = true): String? {
        var address: String? = null
        try {
            address = InetAddress.getByName(domain)?.hostAddress
        } catch (e: UnknownHostException) {
            if (withFailMsg) {
                address = e.message
            }
            Log.e(TAG, "resolveDomain $domain failed", e)
        }
        return address
    }

    suspend fun getLocalNetEnvironment(ipv4OnlyDomain: String?): NetType {
        return when (getIPSupportType()) {
            IPSupportType.NONE -> NetType.UNKNOWN
            IPSupportType.IPV4 -> NetType.IPV4_ONLY
            IPSupportType.IPV6_DUAL -> lookupForNAT64(ipv4OnlyDomain, NetType.IPV6_DUAL)
            IPSupportType.IPV6 -> lookupForNAT64(ipv4OnlyDomain, NetType.IPV6_ONLY)
        }
    }

    private suspend fun lookupForNAT64(ipv4OnlyDomain: String?, originalInferType: NetType): NetType {
        return if (ipv4OnlyDomain.isNullOrEmpty()) {
            originalInferType
        } else {
            val dnsResult = resolveDomain(ipv4OnlyDomain, withFailMsg = false)
            if (dnsResult != null && dnsResult.contains(REG_IPV6)) {
                NetType.IPV6_NAT64
            } else {
                originalInferType
            }
        }
    }

    private suspend fun getIPSupportType(): IPSupportType {
        var type = IPSupportType.NONE
        var process: Process? = null
        var reader: BufferedReader? = null
        try {
            process = Runtime.getRuntime().exec("ifconfig")
            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            var net = NetInterFace()
            val netInterfaces = mutableListOf(net)
            while (line != null) {
                if (line.startsWith("\n") || line.length <= 1) {
                    net = NetInterFace()
                    netInterfaces.add(net)
                }
                net.append(line)
                line = reader.readLine()
            }
            netInterfaces.forEach { Log.i(TAG, it.toString()) }
            val supportV4 = netInterfaces.any { !it.isLocalHost && it.v4IP.size > 0 }
            val supportV6 = netInterfaces.any { !it.isLocalHost && it.hasGlobalV6IP() }
            type = if (supportV4 && supportV6) {
                // there is one condition that one net interface support v4 and another support v6
                // so we have to count all the interfaces to see whether it is a dual stack env
                IPSupportType.IPV6_DUAL
            } else if (supportV4) {
                IPSupportType.IPV4
            } else if (supportV6) {
                IPSupportType.IPV6
            } else {
                IPSupportType.NONE
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            closeSilent(reader)
        }
        return type
    }

    private fun closeSilent(closable: Closeable?) {
        try {
            closable?.close()
        } catch (t: Throwable) {
            // ignore
        }
    }

    class NetInterFace {

        val v4IP = mutableListOf<String?>()
        val v6IP = mutableListOf<String?>()
        var isLocalHost = false

        fun append(line: String) {
            line.toLowerCase().apply {
                var v4Index = indexOf("inet addr:")
                val ipStartIndex = v4Index + "inet addr:".length
                if (v4Index >= 0) {
                    isLocalHost = indexOf("127.0.0.1", ipStartIndex) >= 0
                    val ipEndIndex = indexOf(" ", ipStartIndex)
                    if (ipEndIndex >= 0) {
                        v4IP.add(substring(ipStartIndex, ipEndIndex).trim())
                    }
                } else {
                    var v6Index = indexOf("inet6 addr")
                    if (v6Index >= 0) {
                        val ipStartIndex = v6Index + "inet6 addr:".length
                        isLocalHost = isLocalHost || indexOf("scope: host") >= 0
                        v6IP.add(substring(ipStartIndex).trim())
                    }
                }
            }
        }

        fun hasGlobalV6IP(): Boolean {
            return v6IP.any { it != null && it.contains("global") }
        }

        override fun toString(): String {
            return "NetInterFace: v4IP=$v4IP,\n v6IP=$v6IP,\n isLocalHost=[$isLocalHost]"
        }

    }

}