package com.ggj19.server.api

import com.ggj19.server.clock.Clock
import com.ggj19.server.dtos.Emoji
import com.ggj19.server.dtos.Phase
import com.ggj19.server.dtos.Phase.PHASE_DOOMED
import com.ggj19.server.dtos.Phase.PHASE_EMOJIS
import com.ggj19.server.dtos.Phase.PHASE_ROLE
import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoleThreat
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import com.ggj19.server.random.DefaultRandomGenerator
import com.ggj19.server.random.RandomGenerator
import com.ggj19.server.selectiveMinus
import com.google.common.annotations.VisibleForTesting
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.validation.constraints.NotNull
import javax.ws.rs.ClientErrorException
import javax.ws.rs.GET
import javax.ws.rs.NotFoundException
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType.APPLICATION_JSON

@Path("/game")
@Produces(APPLICATION_JSON)
@Component
@OpenAPIDefinition(info = Info(title = "Game Server", version = "1.0.0"))
@Tag(name = "GameApi")
class GameApi(
  private val clock: Clock
) {
  private val rooms = HashMap<RoomName, RoomState>()
  private val randomGenerators = ConcurrentHashMap<RoomName, RandomGenerator>()

  @GET
  @Path("/start-room")
  @Operation(operationId = "startRoom")
  fun startRoom(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId
  ): RoomInformation {
    val room = getRoom(roomName)

    when (room) {
      is Playing -> throw ClientErrorException("Game has already started", 422)
      is Room -> {
        if (room.owner != playerId) {
          throw ClientErrorException("You're not the owner of the room and hence can't start the room", 422)
        }

        val time = clock.time()
        val randomGenerator = randomGenerators.getValue(roomName)

        val newRoom = Playing(
            players = room.players,
            possibleThreats = room.possibleThreats,
            roundLengthInSeconds = room.roundLengthInSeconds,
            maximumThreats = room.maximumThreats,
            version = 1,
            forbiddenRoles = emptyMap(),
            playedPlayerRoles = emptyMap(),
            playerEmojis = emptyMap(),
            playerEmojisHistory = emptyMap(),
            lastFailedThreats = emptyList(),
            openThreats = randomGenerator.randomElements(room.possibleThreats, room.maxPossibleAmountOfThreats()),
            currentTime = time.toEpochMilli(),
            roundEndingTime = time.plusSeconds(room.roundLengthInSeconds).toEpochMilli(),
            currentPhase = PHASE_EMOJIS,
            currentRoundNumber = 0,
            maxRoundNumber = room.numberOfRounds
        )

        synchronized(rooms) {
          rooms[roomName] = newRoom
        }

        return newRoom.asRoomInformation()
      }
    }
  }

  @GET
  @Path("/create-room")
  @Operation(operationId = "createRoom")
  fun createRoom(
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("possibleThreats") possibleThreats: String,
    @QueryParam("numberOfRounds") numberOfRounds: Int?,
    @QueryParam("roundLengthInSeconds") roundLengthInSeconds: Long?,
    @QueryParam("maximumThreats") maximumThreats: Int?,
    @QueryParam("seed") seed: Long?
  ): Room {
    val encodedPossibleThreats = possibleThreats.split(',')
        .filterNot { it.isBlank() }
        .map { RoleThreat(it.trim()) }
    if (encodedPossibleThreats.size < 5) throw ClientErrorException("Not allowed to create a room with less than 5 possible threats", 422)

    // For the Game Jam we will assume we won't clash and override a room with the same name.
    val randomGenerator: RandomGenerator = DefaultRandomGenerator(seed)
    val roomName = RoomName(randomGenerator.generateRoomName())
    randomGenerators[roomName] = randomGenerator

    val room = RoomState.Room(listOf(playerId), encodedPossibleThreats, roomName, playerId,
        roundLengthInSeconds = roundLengthInSeconds ?: 10L, numberOfRounds = numberOfRounds ?: 5 + randomGenerator.nextInt(10),
        maximumThreats = maximumThreats ?: 4
    )

    synchronized(rooms) {
      rooms[roomName] = room
      return room
    }
  }

  @GET
  @Path("/join-room")
  @Operation(operationId = "joinRoom")
  fun joinRoom(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId
  ): RoomInformation {
    val room = getRoom(roomName) { if (it.players.contains(playerId)) throw ClientErrorException("You are already part of the room with the name: ${roomName.name}", 422) }
    val newRoom = room.copyJoining(playerId)

    synchronized(rooms) {
      rooms[roomName] = newRoom
      return newRoom.asRoomInformation()
    }
  }

  @GET
  @Path("/room-information")
  @Operation(operationId = "roomInformation")
  fun roomInformation(
    @NotNull @QueryParam("roomName") roomName: RoomName
  ): RoomInformation {
    val room = getRoom(roomName)

    return when (room) {
      is Room -> room.asRoomInformation()
      is Playing -> {
        val time = clock.time()
        val isRoundDue = Instant.ofEpochMilli(room.roundEndingTime) >= time

        if (isRoundDue) {
          val currentPhase = room.currentPhase
          val randomGenerator = randomGenerators.getValue(roomName)

          var newRoom = when (currentPhase) {
            PHASE_EMOJIS -> room.copy(
                version = room.version + 1,
                currentTime = time.toEpochMilli(),
                roundEndingTime = time.plusSeconds(room.roundLengthInSeconds).toEpochMilli(),
                currentPhase = PHASE_ROLE
            )
            PHASE_ROLE -> room.copy(
                version = room.version + 1,
                forbiddenRoles = room.playedPlayerRoles,
                playedPlayerRoles = emptyMap(),
                playerEmojis = emptyMap(),
                playerEmojisHistory = room.players.map { playerId -> playerId to room.playerEmojisHistory.getOrDefault(playerId, listOf()) + listOf(room.playerEmojis[playerId] ?: emptyList()) }.toMap(),
                lastFailedThreats = room.lastFailedThreats + room.openThreats.selectiveMinus(room.playedPlayerRoles.values),
                openThreats = randomGenerator.randomElements(room.possibleThreats, room.maxPossibleAmountOfThreats()),
                currentTime = time.toEpochMilli(),
                roundEndingTime = time.plusSeconds(room.roundLengthInSeconds).toEpochMilli(),
                currentPhase = PHASE_EMOJIS,
                currentRoundNumber = room.currentRoundNumber + 1
            )
            PHASE_DOOMED -> room // Forward the current state.
          }

          if (newRoom.lastFailedThreats.size + newRoom.openThreats.size >= room.maximumThreats) {
            newRoom = newRoom.copy(currentPhase = PHASE_DOOMED)
          }

          synchronized(rooms) { rooms[roomName] = newRoom }
          newRoom.asRoomInformation()
        } else {
          room.asRoomInformation()
        }
      }
    }
  }

  @GET
  @Path("/send-emojis")
  @Operation(operationId = "sendEmojis")
  fun sendEmojis(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("emojis") emojis: String
  ): RoomInformation {
    val room = getRoom(roomName) {
      if (it is Room) throw ClientErrorException("Game hasn't started", 422)
      if (!it.players.contains(playerId)) throw ClientErrorException("You are not part of the room with the name: ${roomName.name}", 422)
      if (it is Playing && it.currentPhase != PHASE_EMOJIS) throw ClientErrorException("Game is not in emoji phase", 422)
    }

    val encodedEmojis = emojis.split(',')
        .filterNot { it.isBlank() }
        .map { Emoji(it.trim()) }

    if (encodedEmojis.isEmpty() || encodedEmojis.size > 2) throw ClientErrorException("You must send between one and two Emojis :(", 422)

    synchronized(rooms) {
      val new = (room as Playing).copy(version = room.version + 1, playerEmojis = room.playerEmojis.plus(playerId to encodedEmojis))
      rooms[roomName] = new
      return new.asRoomInformation()
    }
  }

  @GET
  @Path("/set-role")
  @Operation(operationId = "setRole")
  fun setRole(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("role") role: RoleThreat
  ): RoomInformation {
    val room = getRoom(roomName) {
      if (it is Room) throw ClientErrorException("Game hasn't started", 422)
      if (!it.players.contains(playerId)) throw ClientErrorException("You are not part of the room with the name: ${roomName.name}", 422)
      if (it is Playing && it.currentPhase != PHASE_ROLE) throw ClientErrorException("Game is not in role phase", 422)
    }

    synchronized(rooms) {
      val new = (room as Playing).copy(version = room.version + 1, playedPlayerRoles = room.playedPlayerRoles.plus(playerId to role))
      rooms[roomName] = new
      return new.asRoomInformation()
    }
  }

  private inline fun getRoom(roomName: RoomName, validation: (RoomState) -> Unit = { }): RoomState {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw NotFoundException("Can't find a room with the name: ${roomName.name}")
      validation.invoke(room)
      return room
    }
  }

  @VisibleForTesting fun changePhase(roomName: RoomName, phase: Phase): RoomInformation {
    val room = getRoom(roomName)

    synchronized(rooms) {
      val new = (room as Playing).copy(currentPhase = phase)
      rooms[roomName] = new
      return new.asRoomInformation()
    }
  }
}
