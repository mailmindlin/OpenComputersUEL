package li.cil.oc.util

import com.google.common.net.InetAddresses
import li.cil.oc.OpenComputers
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class InternetFilteringRule(val ruleString: String) {
    private var _invalid: Boolean = false
    private val validator: (InetAddress, String) -> Boolean?

    init {
        validator = try {
            val ruleParts = ruleString.split(' ')
            when (ruleParts.first()) {
                "allow", "deny" -> {
                    val value = ruleParts.first() == "allow"
                    val predicates = mutableListOf<(InetAddress, String) -> Boolean>()
                    ruleParts.drop(1).forEach { f ->
                        val filter = f.split(":", limit = 2)
                        when (filter.first()) {
                            "default" -> {
                                if (!value) {
                                    predicates.add { _, _ -> false }
                                } else {
                                    predicates.add { inetAddress, host ->
                                        defaultRules.asSequence()
                                            .map { r -> r.apply(inetAddress, host) }
                                            .firstOrNull { it != null } ?: false
                                    }
                                }
                            }
                            "private" -> {
                                predicates.add { inetAddress, _ ->
                                    inetAddress.isAnyLocalAddress || inetAddress.isLoopbackAddress ||
                                        inetAddress.isLinkLocalAddress || inetAddress.isSiteLocalAddress
                                }
                            }
                            "bogon" -> {
                                predicates.add { inetAddress, _ ->
                                    bogonMatchingRules.any { rule -> rule.matches(inetAddress) }
                                }
                            }
                            "ipv4" -> {
                                predicates.add { inetAddress, _ ->
                                    inetAddress is Inet4Address
                                }
                            }
                            "ipv6" -> {
                                predicates.add { inetAddress, _ ->
                                    inetAddress is Inet6Address
                                }
                            }
                            "ipv4-embedded-ipv6" -> {
                                predicates.add { inetAddress, _ ->
                                    inetAddress is Inet6Address && InetAddresses.hasEmbeddedIPv4ClientAddress(inetAddress)
                                }
                            }
                            "domain" -> {
                                val domain = filter[1]
                                val addresses = InetAddress.getAllByName(domain)
                                predicates.add { inetAddress, host ->
                                    host == domain || addresses.any { a -> a == inetAddress }
                                }
                            }
                            "ip" -> {
                                val ipStringParts = filter[1].split("/", limit = 2)
                                if (ipStringParts.size == 2) {
                                    val ipRange = InetAddressRange.parse(ipStringParts[0], ipStringParts[1])
                                    predicates.add { inetAddress, _ -> ipRange.matches(inetAddress) }
                                } else {
                                    val ipAddress = InetAddresses.forString(ipStringParts[0])
                                    predicates.add { inetAddress, _ -> ipAddress == inetAddress }
                                }
                                predicates.add { inetAddress, _ ->
                                    inetAddress.isAnyLocalAddress || inetAddress.isLoopbackAddress ||
                                        inetAddress.isLinkLocalAddress || inetAddress.isSiteLocalAddress
                                }
                            }
                            "all" -> { /* no predicate needed */ }
                        }
                    };

                    { inetAddress: InetAddress, host: String ->
                        if (predicates.all { p -> p(inetAddress, host) }) value else null
                    }
                }
                "removeme" -> {
                    // Ignore this rule.
                    { _, _ -> null }
                }
                else -> {
                    { _, _ -> null }
                }
            }
        } catch (t: Throwable) {
            OpenComputers.log.error("Invalid Internet filteringRules rule in configuration: \"$ruleString\".", t)
            _invalid = true
            { _, _ -> false }
        }
    }

    fun invalid(): Boolean = _invalid

    fun apply(inetAddress: InetAddress, host: String): Boolean? = validator(inetAddress, host)

    companion object {
        private val defaultRules = arrayOf(
            InternetFilteringRule("deny private"),
            InternetFilteringRule("deny bogon"),
            InternetFilteringRule("allow all")
        )

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
            .map { s -> s.split("/", limit = 2) }
            .map { s -> InetAddressRange.parse(s[0], s[1]) }
            .toTypedArray()
    }
}
