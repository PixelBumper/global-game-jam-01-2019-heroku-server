package com.ggj19.server.dtos

import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import java.time.Instant

inline class RoleThreat(val value: String)
inline class PlayerId(val name: String)
inline class RoomName(val name: String)

sealed class RoomState {
  abstract val players: List<PlayerId>
  abstract val possibleThreats: List<RoleThreat>
  abstract fun copyJoining(playerId: PlayerId): RoomState
  
  data class Room(
    override val players: List<PlayerId>,
    override val possibleThreats: List<RoleThreat>,
    val name: RoomName,
    val owner: PlayerId
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId))
  }

  data class Playing(
    override val players: List<PlayerId>,
    override val possibleThreats: List<RoleThreat>,
    val forbiddenRoles: Map<PlayerId, RoleThreat>,
    val lastFailedThreats: List<RoleThreat>,
    val openThreats: List<RoleThreat>,
    val roundEndingTime: Instant,
    val currentRoundState: RoundState,
    val currentRoundNumber: Int,
    val maxRoundNumber: Int,
    val gameWon: Boolean = currentRoundNumber == maxRoundNumber
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId))
  }

  fun asRoomInformation() = RoomInformation(this as? Room, this as? Playing)
}

enum class RoundState {
  COMMUNICATION_PHASE,
  PLAYOUT_PHASE,
  DOOMED_PHASE
}

data class RoomInformation(
  val waiting: Room?,
  val playing: Playing?
)
