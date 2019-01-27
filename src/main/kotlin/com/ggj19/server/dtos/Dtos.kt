package com.ggj19.server.dtos

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room

typealias RoleThreat = String
typealias PlayerId = String
typealias RoomName = String
typealias Emoji = String

sealed class RoomState {
  abstract val players: List<PlayerId>
  abstract val possibleThreats: List<RoleThreat>
  abstract val roundLengthInSeconds: Long
  abstract val maximumThreats: Int
  abstract fun copyJoining(playerId: PlayerId): RoomState
  
  data class Room(
    override val players: List<PlayerId>,
    override val possibleThreats: List<RoleThreat>,
    val name: RoomName,
    val owner: PlayerId,
    @JsonIgnore override val roundLengthInSeconds: Long,
    val numberOfRounds: Int,
    override val maximumThreats: Int
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId))
  }

  data class Playing(
    override val players: List<PlayerId>,
    override val possibleThreats: List<RoleThreat>,
    override val maximumThreats: Int,
    @JsonIgnore override val roundLengthInSeconds: Long,
    val version: Int,
    val forbiddenRoles: Map<PlayerId, RoleThreat>,
    val playedPlayerRoles: Map<PlayerId, RoleThreat>,
    val playerEmojis: Map<PlayerId, List<Emoji>>,
    val playerEmojisHistory: Map<PlayerId, List<List<Emoji>>>,
    val lastFailedThreats: List<RoleThreat>,
    val openThreats: List<RoleThreat>,
    val currentTime: Long,
    val roundEndingTime: Long,
    val currentPhase: Phase,
    val currentRoundNumber: Int,
    val maxRoundNumber: Int,
    val gameWon: Boolean = currentRoundNumber == maxRoundNumber
  ) : RoomState() {
    override fun copyJoining(playerId: PlayerId) = copy(players = players.plus(playerId), version = version + 1)
  }

  fun maxPossibleAmountOfThreats() = minOf(players.size, possibleThreats.size)
  fun asRoomInformation() = RoomInformation(this as? Room, this as? Playing)
}

enum class Phase {
  PHASE_EMOJIS,
  PHASE_ROLE,
  PHASE_DOOMED
}

@JsonInclude(value = Include.NON_NULL)
data class RoomInformation(
  val waiting: Room?,
  val playing: Playing?
)
