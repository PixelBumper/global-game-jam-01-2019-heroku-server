package com.ggj19.server.dtos

sealed class RoomState {
  abstract val players: List<PlayerId>

  data class Room(
    val name: RoomName,
    val owner: PlayerId,
    override val players: List<PlayerId>
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId))
  }

  data class Playing(
    override val players: List<PlayerId>,
    val foo: Int = 1,
    val bar: Int = 2
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId))
  }

  abstract fun copyJoining(playerId: PlayerId): RoomState
}

fun RoomState.asRoomInformation(): RoomInformation {
  val waiting = if (this is RoomState.Room) this else null
  val playing = if (this is RoomState.Playing) this else null
  return RoomInformation(waiting, playing)
}
