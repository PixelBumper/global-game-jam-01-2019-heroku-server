package com.ggj19.server.random

import com.github.javafaker.Faker
import java.util.Random

class DefaultRandomGenerator(
  private val seed: Long?
) : RandomGenerator {
  private val random = Random().apply { if (seed != null) setSeed(seed) }
  private val faker = Faker(random)

  override fun generateRoomName() = faker.funnyName().name()

  override fun nextInt(bounds: Int) = random.nextInt(bounds)

  override fun <T> randomElements(list: List<T>, numberOfElements: Int): List<T> {
    val set = mutableSetOf<T>()

    while (set.size != numberOfElements) {
      set.add(list[random.nextInt(list.size)])
    }

    return set.toList()
  }
}
