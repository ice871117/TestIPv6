package com.tencent.component.ipv6

import com.tencent.testipv6.IPEnvironment

object IPUtils {

    init {
        System.loadLibrary("radio_net")
    }

    private val REG_IPV6 =
        Regex("^\\[?s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*]?\$")
    private val REG_IPV4 = Regex("^((?:(?:25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(?:25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d))))\$")

    /**
     * Whether the given ip expression is a valid ipv4 or ipv6 format.
     */
    fun isIPAddress(ipExpr: String?): Boolean {
        return isValidIPv4(ipExpr) || isValidIPv6(ipExpr)
    }

    /**
     * Whether the given ip expression is valid ipv4 format
     * <p> NOTE THAT the space character around the given expression should be trimmed by the invoker
     * <p> or the test would be failed.
     * <p> e.g. 192.168.1.1
     */
    fun isValidIPv4(ipExpr: String?): Boolean {
        if (ipExpr.isNullOrEmpty()) {
            return false
        }
        return REG_IPV4.matches(ipExpr!!)
    }

    /**
     * Whether the given ip expression is a valid ipv6 format
     * <p> NOTE THAT the space character around the given expression should be trimmed by the invoker
     * <p> or the test would be failed.
     * <p> e.g. 240e:1a:e6:200:0:0:0:c
     */
    fun isValidIPv6(ipExpr: String?): Boolean {
        if (ipExpr.isNullOrEmpty()) {
            return false
        }
        return REG_IPV6.matches(ipExpr!!)
    }

    fun convertToIPv6Format(ip: String, port: String?): String {
        return if (port.isNullOrEmpty()) {
            if (ip.contains("[")) {
                ip
            } else {
                "[$ip]"
            }
        } else {
            if (ip.contains("[")) {
                "$ip:$port"
            } else {
                "[$ip:$port]"
            }
        }
    }

    fun convertToIPv6Format(ip: String, port: Int): String {
        return if (ip.contains("[")) {
            "$ip:$port"
        } else {
            "[$ip]:$port"
        }
    }

    fun convertToIPv4Format(ip: String, port: String?): String {
        return if (port.isNullOrEmpty()) {
            ip
        } else {
            "$ip:$port"
        }
    }

    fun convertToIPv4Format(ip: String, port: Int) = "$ip:$port"

    fun supportIPv4(netType: IPEnvironment.NetType): Boolean{
        return when (netType) {
            IPEnvironment.NetType.UNKNOWN, IPEnvironment.NetType.IPV6_NAT64, IPEnvironment.NetType.IPV6_DUAL,  IPEnvironment.NetType.IPV4_ONLY -> true
            else -> false
        }
    }

    fun supportIPv6(netType: IPEnvironment.NetType): Boolean{
        return when (netType) {
            IPEnvironment.NetType.UNKNOWN, IPEnvironment.NetType.IPV6_NAT64, IPEnvironment.NetType.IPV6_DUAL,  IPEnvironment.NetType.IPV6_ONLY -> true
            else -> false
        }
    }

    /**
     * native get net type
     * @return 0 means none, 1 mean has at least one ipv4 global address
     */
    external fun hasGlobalV4Addr(): Int
}