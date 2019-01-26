package com.ggj19.server.random

interface RandomGenerator {
  fun generateRoomName(): String

  fun nextInt(bounds: Int): Int

  fun <T> randomElements(list: List<T>, numberOfElements: Int): List<T>
}
