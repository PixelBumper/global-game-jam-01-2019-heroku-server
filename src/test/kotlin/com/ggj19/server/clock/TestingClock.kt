package com.ggj19.server.clock

import java.time.Instant

class TestingClock(var time: Instant) : Clock {
  override fun time(): Instant = time
}
