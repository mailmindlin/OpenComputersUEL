package li.cil.oc.util

import org.junit.jupiter.api.Assertions.*
import li.cil.oc.Settings
import li.cil.oc.server.component.InternetCard
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import java.net.InetAddress
import java.net.URI

class InternetFilteringRuleTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun setUp() {
      Settings.defaultsForTesting()
    }
  }

  // Many of these payloads are pulled from PayloadsAllTheThings
  // https://github.com/swisskyrepo/PayloadsAllTheThings/blob/master/Server%20Side%20Request%20Forgery/README.md

  @Test
  fun `default should accept a valid external address`() {
    assertNotBlacklisted("https://google.com")
  }
  @Test
  fun `default should reject localhost`() {
    assertBlacklisted("http://localhost")
  }
  @Test
  fun `default reject the local host in IPv4 format`() {
    assertBlacklisted("http://127.0.0.1")
    assertBlacklisted("http://127.0.1")
    assertBlacklisted("http://127.1")
    assertBlacklisted("http://0")
  }

  @Test
  fun `default reject the local host in IPv6`() {
    assertBlacklisted("http://[::1]")
    assertBlacklisted("http://[::]")
  }

  @Test
  fun `default should reject IPv6 & IPv4 Address Embedding`() {
    assertBlacklisted("http://[0:0:0:0:0:ffff:127.0.0.1]")
    assertBlacklisted("http://[::ffff:127.0.0.1]")
  }
  @Test
  fun `default should reject an attempt to bypass using a decimal IP location`() {
    assertBlacklisted("http://2130706433") // 127.0.0.1
    assertBlacklisted("http://3232235521") // 192.168.0.1
    assertBlacklisted("http://3232235777") // 192.168.1.1
  }
  @Test
  fun `default should reject the IMDS address in IPv4 format`() {
    assertBlacklisted("http://169.254.169.254")
    assertBlacklisted("http://2852039166") // 169.254.169.254
  }
  @Test
  fun `default should reject the IMDS address in IPv6 format`() {
    assertBlacklisted("http://[fd00:ec2::254]")
  }
  @Test
  fun `default should reject the IMDS in for Oracle Cloud`() {
    assertBlacklisted("http://192.0.0.192")
  }
  @Test
  fun `default should reject the IMDS in for Alibaba Cloud`() {
    assertBlacklisted("http://100.100.100.200")
  }
  private fun assertBlacklisted(uri: String) {
    assertTrue(isUriBlacklisted(uri)) { "Did not blacklist \"$uri\"" }
  }
  private fun assertNotBlacklisted(uri: String) {
    assertFalse(isUriBlacklisted(uri)) { "Incorrectly blacklisted \"$uri\"" }
  }
  private fun isUriBlacklisted(uri: String): Boolean {
    val uriObj = URI(uri)
    val resolved = InetAddress.getByName(uriObj.host)
    return !InternetCard.isRequestAllowed(Settings.get, resolved, uriObj.host)
  }
}
