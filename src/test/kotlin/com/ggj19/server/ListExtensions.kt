package com.ggj19.server

import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.Test

class ListExtensions {
  @Test fun selectiveMinus() {
    assertThat(listOf("PILOT").selectiveMinus(emptyList())).containsExactly("PILOT")
    assertThat(listOf("PILOT").selectiveMinus(listOf("PILOT"))).isEmpty()
    assertThat(listOf("PILOT").selectiveMinus(listOf("PILOT", "PILOT"))).containsExactly("PILOT")

    assertThat(listOf("PILOT", "PILOT").selectiveMinus(listOf("PILOT", "PILOT"))).isEmpty()
    assertThat(listOf("PILOT", "PILOT").selectiveMinus(listOf("PILOT"))).containsExactly("PILOT")
    assertThat(listOf("PILOT", "PILOT").selectiveMinus(listOf("PILOT", "PILOT", "PILOT"))).containsExactly("PILOT", "PILOT")

    assertThat(listOf("PILOT", "PILOT", "LAZY").selectiveMinus(listOf("PILOT", "PILOT"))).containsExactly("LAZY")
    assertThat(listOf("PILOT", "PILOT", "LAZY").selectiveMinus(listOf("PILOT", "PILOT", "LAZY"))).isEmpty()
  }
}
