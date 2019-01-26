package com.ggj19.server

import com.ggj19.server.clock.Clock
import com.ggj19.server.clock.SystemClock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration class ServerConfiguration {
  @Bean fun provideClock(): Clock = SystemClock()
}
