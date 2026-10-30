package ai.threemi.tvtimer

import java.net.Inet4Address
import java.net.NetworkInterface

object Net {
  /** 電視在家裡網路上的 IP(有線、Wi-Fi 都可以) */
  fun localIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
      .filter { it.isUp && !it.isLoopback }
      .flatMap { it.inetAddresses.toList() }
      .filterIsInstance<Inet4Address>()
      .firstOrNull { it.isSiteLocalAddress }?.hostAddress
  }.getOrNull()
}
