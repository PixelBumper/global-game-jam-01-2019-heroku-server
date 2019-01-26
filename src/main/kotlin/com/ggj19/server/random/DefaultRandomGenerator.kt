package com.ggj19.server.random

import com.github.javafaker.Faker
import java.util.Random

class DefaultRandomGenerator(
  private val seed: Long?
) : RandomGenerator {
  private val random = Random().apply { if (seed != null) setSeed(seed) }
  private val faker = Faker(random)

  override fun generateRoomName() = faker.name().firstName()

  override fun nextInt(bounds: Int) = random.nextInt(bounds)

  override fun <T> randomElements(list: List<T>, numberOfElements: Int): List<T> {
    val new = mutableListOf<T>()

    while (new.size < numberOfElements) {
      new.add(list[random.nextInt(list.size)])
    }

    return new.toList()
  }
}
