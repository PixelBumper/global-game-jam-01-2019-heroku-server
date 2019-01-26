package com.ggj19.server.dtos

import com.fasterxml.jackson.annotation.JsonIgnore
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import java.time.Instant

inline class RoleThreat(val value: String)
inline class PlayerId(val name: String)
inline class RoomName(val name: String)
inline class Emoji(val emoji: String)

sealed class RoomState {
  abstract val players: List<PlayerId>
  abstract val possibleThreats: List<RoleThreat>
  abstract val roundLengthInSeconds: Long
  abstract fun copyJoining(playerId: PlayerId): RoomState
  
  data class Room(
    override val players: List<PlayerId>,
    override val possibleThreats: List<RoleThreat>,
    @JsonIgnore override val roundLengthInSeconds: Long,
    val name: RoomName,
    val owner: PlayerId
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId))
  }

  data class Playing(
    override val players: List<PlayerId>,
    override val possibleThreats: List<RoleThreat>,
    @JsonIgnore override val roundLengthInSeconds: Long,
    val version: Int,
    val forbiddenRoles: Map<PlayerId, RoleThreat>,
    val playedPlayerRoles: Map<PlayerId, RoleThreat>,
    val playerEmojis: Map<PlayerId, List<Emoji>>,
    val playerEmojisHistory: Map<PlayerId, List<List<Emoji>>>,
    val lastFailedThreats: List<RoleThreat>,
    val openThreats: List<RoleThreat>,
    val roundEndingTime: Instant,
    val currentPhase: Phase,
    val currentRoundNumber: Int,
    val maxRoundNumber: Int,
    val gameWon: Boolean = currentRoundNumber == maxRoundNumber
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId), version = version + 1)
  }

  fun asRoomInformation() = RoomInformation(this as? Room, this as? Playing)
}

enum class Phase {
  PHASE_EMOJIS,
  PHASE_ROLE,
  PHASE_DOOMED
}

data class RoomInformation(
  val waiting: Room?,
  val playing: Playing?
)
