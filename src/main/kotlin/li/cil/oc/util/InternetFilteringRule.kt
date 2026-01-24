package li.cil.oc.util

import com.google.common.net.InetAddresses
import li.cil.oc.OpenComputers
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal sealed interface Filter {
    fun matches(inetAddress: InetAddress, host: String): Boolean

    object IPv4: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = inetAddress is Inet4Address
    }
    object IPv6: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = inetAddress is Inet6Address
    }
    object IPv4EmbeddedIPv6: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = inetAddress is Inet6Address && InetAddresses.hasEmbeddedIPv4ClientAddress(inetAddress)
    }
    object Private: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean
            = inetAddress.isAnyLocalAddress
            || inetAddress.isLoopbackAddress
            || inetAddress.isLinkLocalAddress
            || inetAddress.isSiteLocalAddress
    }
    object Bogon: Filter {
        private val bogonMatchingRules = arrayOf(
            "0.0.0.0/8",
            "10.0.0.0/8",
            "100.64.0.0/10",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.0.0.0/24",
            "192.0.2.0/24",
            "192.168.0.0/16",
            "198.18.0.0/15",
            "198.51.100.0/24",
            "203.0.113.0/24",
            "224.0.0.0/3",
            "::/128",
            "::1/128",
            "::ffff:0:0/96",
            "::/96",
            "100::/64",
            "2001:10::/28",
            "2001:db8::/32",
            "fc00::/7",
            "fe80::/10",
            "fec0::/10",
            "ff00::/8"
        )
            .map { it.split("/", limit = 2) }
            .map { InetAddressRange.parse(it[0], it[1]) }
            .toTypedArray()
        override fun matches(inetAddress: InetAddress, host: String): Boolean
            = bogonMatchingRules.any { it.matches(inetAddress) }
    }
    class Domain(private val domain: String): Filter {
        private val addresses: Array<out InetAddress> = InetAddress.getAllByName(domain)
        override fun matches(inetAddress: InetAddress, host: String): Boolean
            = host == domain && addresses.any { it == inetAddress }
    }
    class IpAddress(private val address: InetAddress): Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = inetAddress == address && Private.matches(inetAddress, host)
    }
    class IpRange(private val range: InetAddressRange): Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = range.matches(inetAddress) && Private.matches(inetAddress, host)
    }
    object Never: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = false
    }
    object Always: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean = true
    }

    /**
     * Default for `allow default`. Equivalent to `"deny private"`, `"deny bogon"`, `"allow all"`
     */
    object Default: Filter {
        override fun matches(inetAddress: InetAddress, host: String): Boolean
            // deny private
            = !Private.matches(inetAddress, host)
            // deny bogon
            && !Bogon.matches(inetAddress, host)
            // allow all
            && Always.matches(inetAddress, host)
    }

    companion object {
        internal fun parse(filter: String, value: Boolean): Filter {
            val filter = filter.split(":", limit = 2)
            return when (filter.first()) {
                "default" -> if (!value) Filter.Never else Filter.Default
                "private" -> Filter.Private
                "bogon" -> Filter.Bogon
                "ipv4" -> Filter.IPv4
                "ipv6" -> Filter.IPv6
                "ipv4-embedded-ipv6" -> Filter.IPv4EmbeddedIPv6
                "domain" -> Filter.Domain(filter[1])
                "ip" -> {
                    val ipStringParts = filter[1].split("/", limit = 2)
                    if (ipStringParts.size == 2) {
                        Filter.IpRange(InetAddressRange.parse(ipStringParts[0], ipStringParts[1]))
                    } else {
                        Filter.IpAddress(InetAddresses.forString(ipStringParts[0]))
                    }
                }
                "all" -> Filter.Always
                else -> throw IllegalArgumentException("Unknown filter rule ${filter.first()}")
            }
        }
    }
}

sealed interface InternetFilteringRule {
    fun invalid(): Boolean = false

    /**
     * Evaluate this rule against a resolved host
     *
     * @return true/false if accepted/rejected, or null if not applicable
     */
    fun apply(inetAddress: InetAddress, host: String): Boolean?;

    object Invalid: InternetFilteringRule {
        override fun invalid(): Boolean = true
        override fun apply(inetAddress: InetAddress, host: String): Boolean? = false
    }
    object Ignore: InternetFilteringRule {
        override fun apply(inetAddress: InetAddress, host: String): Boolean? = null
    }
    class All internal constructor(private val allow: Boolean, private vararg val rules: Filter): InternetFilteringRule {
        override fun apply(inetAddress: InetAddress, host: String): Boolean? = if (rules.all { it.matches(inetAddress, host) }) allow else null
    }

    companion object {
        @JvmStatic
        fun parse(ruleString: String): InternetFilteringRule {
            return try {
                val ruleParts = ruleString.split(' ')
                when (ruleParts.first()) {
                    "allow", "deny" -> {
                        val value = ruleParts.first() == "allow"
                        val predicates = ruleParts
                            .asSequence()
                            .drop(1)
                            .map { Filter.parse(it, value) }
                            .toList()
                            .toTypedArray()
                        return InternetFilteringRule.All(value, *predicates)
                    }
                    // Ignore this rule.
                    "removeme" -> Ignore
                    else -> Ignore
                }
            } catch (e: Exception) {
                OpenComputers.log.error("Invalid Internet filteringRules rule in configuration: \"$ruleString\".", e)
                Invalid
            }
        }
    }
}
