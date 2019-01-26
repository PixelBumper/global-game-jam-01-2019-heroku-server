package com.ggj19.server.clock

import java.time.Instant

class SystemClock : Clock {
  override fun time() = Instant.now()
}
