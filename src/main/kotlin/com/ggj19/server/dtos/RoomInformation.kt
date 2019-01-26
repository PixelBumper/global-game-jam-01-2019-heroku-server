package com.ggj19.server.dtos

import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room

data class RoomInformation(
  val waiting: Room?,
  val playing: Playing?
)
