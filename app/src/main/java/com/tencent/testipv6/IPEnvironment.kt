package com.tencent.testipv6

import android.util.Log
import com.tencent.component.ipv6.IPUtils
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.UnknownHostException

object IPEnvironment {

    const val TAG = "IPEnvironment"

    /**
     *  define a group of special ipv6 address prefix that should be filtered
     *  see detail in https://en.wikipedia.org/wiki/Reserved_IP_addresses
     */
    private val SPECIAL_IPV6_PREFIX = listOf("::1", "fc00::", "fe80::", "ff00::")

    val REG_IPV6 = Regex("^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$")

    enum class NetType {
        UNKNOWN, IPV4_ONLY, IPV6_ONLY, IPV6_DUAL, IPV6_NAT64
    }

    internal enum class IPSupportType {
        NONE, IPV4, IPV6, IPV6_DUAL
    }

    fun resolveDomain(domain: String, withFailMsg: Boolean = true): String? {
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
            IPSupportType.NONE -> detectByNative()
            IPSupportType.IPV4 -> NetType.IPV4_ONLY
            IPSupportType.IPV6_DUAL -> lookupForNAT64(ipv4OnlyDomain, NetType.IPV6_DUAL)
            IPSupportType.IPV6 -> lookupForNAT64(ipv4OnlyDomain, NetType.IPV6_ONLY)
        }
    }

    private fun detectByNative(): NetType {
        val nativeRet = IPUtils.hasGlobalV4Addr()
        val hasV4Addr = nativeRet == 1 || nativeRet < 0 // if native run with error, we assume it is ipv4 supported env
        val hasV6Addr = hasGlobalV6ViaProc()
        return when {
            hasV4Addr && hasV6Addr -> NetType.IPV6_DUAL
            hasV4Addr -> NetType.IPV4_ONLY
            hasV6Addr -> NetType.IPV6_ONLY
            else -> NetType.UNKNOWN
        }
    }

    private fun hasGlobalV6ViaProc(): Boolean {
        var hasGlobalV6Addr = false
        var process: Process? = null
        var reader: BufferedReader? = null
        try {
            process = Runtime.getRuntime().exec("cat /proc/net/if_inet6")
            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                line.trim().let {
                    trimmedIp ->
                    hasGlobalV6Addr = !SPECIAL_IPV6_PREFIX.any { trimmedIp.startsWith(it) }
                }
                if (hasGlobalV6Addr) {
                    break
                }
                line = reader.readLine()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            closeSilently(reader)
            destroySilently(process)
        }
        return hasGlobalV6Addr
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

    private fun getIPSupportType(): IPSupportType {
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
            val supportV4 = netInterfaces.any { it.hasGlobalV4IP() }
            val supportV6 = netInterfaces.any { it.hasGlobalV6IP() }
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
            closeSilently(reader)
            destroySilently(process)
        }
        return type
    }

    private fun closeSilently(closable: Closeable?) {
        try {
            closable?.close()
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun destroySilently(process: Process?) {
        try {
            process?.destroy()
        } catch (e: Throwable) {
            // ignore
        }
    }


    internal class NetInterFace {

        private var name: String? = null
        private val v4IP = mutableListOf<Pair<String?, Boolean>>()
        private val v6IP = mutableListOf<Pair<String?, Boolean>>()

        fun append(line: String) {
            if (name.isNullOrEmpty()) {
                val blankIndex = line.indexOf(' ')
                name = if (blankIndex >= 0) {
                    line.substring(0, blankIndex)
                } else {
                    line
                }
            }
            line.toLowerCase().apply {
                val v4Index = indexOf("inet addr:")
                var ipStartIndex = v4Index + "inet addr:".length
                if (v4Index >= 0) {
                    val ipEndIndex = indexOf(" ", ipStartIndex)
                    val trimmedIp = if (ipEndIndex >= 0) {
                        substring(ipStartIndex, ipEndIndex).trim()
                    } else {
                        substring(ipStartIndex).trim()
                    }
                    v4IP.add(Pair(trimmedIp, trimmedIp.startsWith("127.")))
                } else {
                    val v6Index = indexOf("inet6 addr:")
                    if (v6Index >= 0) {
                        ipStartIndex = v6Index + "inet6 addr:".length
                        val trimmedIp = substring(ipStartIndex).trim()
                        val isSpecialIP = SPECIAL_IPV6_PREFIX.any { trimmedIp.startsWith(it) }
                        v6IP.add(Pair(trimmedIp, isSpecialIP))
                    }
                }
            }
        }

        fun hasGlobalV6IP() = v6IP.any { it.first != null && !it.second }

        fun hasGlobalV4IP() = v4IP.any { it.first != null && !it.second }

        override fun toString(): String {
            return "NetInterface: $name \nv4IP=$v4IP,\n v6IP=$v6IP"
        }

    }

}