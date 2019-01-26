package com.ggj19.server.clock

import java.time.Instant

interface Clock {
  fun time(): Instant
}
