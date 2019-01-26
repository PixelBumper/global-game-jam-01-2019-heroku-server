package com.ggj19.server

import java.util.Random

fun <T> List<T>.random(random: Random, numberOfElements: Int): List<T> {
  val set = mutableSetOf<T>()

  while (set.size != numberOfElements) {
    set.add(this[random.nextInt(size)])
  }

  return set.toList()
}
