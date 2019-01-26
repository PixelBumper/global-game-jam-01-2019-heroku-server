package com.ggj19.server

fun <T> List<T>.selectiveMinus(other: Collection<T>) = this.groupBy { it }
    .mapValues { it.value.size }
    .mapValues { (key, value) ->
      val remaining = value - other.count { it == key }
      when {
        remaining == 0 -> emptyList()
        remaining < 0 -> (0 until value).map { key }
        else -> (0 until remaining).map { key }
      }
    }
    .values
    .flatten()
