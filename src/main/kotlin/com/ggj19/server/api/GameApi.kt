package com.ggj19.server.api

import com.ggj19.server.clock.Clock
import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoleThreat
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import com.ggj19.server.dtos.RoundState.COMMUNICATION_PHASE
import com.ggj19.server.random.DefaultRandomGenerator
import com.ggj19.server.random.RandomGenerator
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    val newRoom: RoomState

    when (room) {
      is Playing -> throw ClientErrorException("Game has already started", 422)
      is Room -> {
        if (room.owner != playerId) {
          throw ClientErrorException("You're not the owner of the room and hence can't start the room", 422)
        }

        val randomGenerator = randomGenerators.getValue(roomName)

        val maxPossibleAmount = minOf(room.players.size, room.possibleThreats.size)
        val numberOfThreats = maxOf(1, randomGenerator.nextInt(maxPossibleAmount))

        newRoom = Playing(
            players = room.players,
            possibleThreats = room.possibleThreats,
            forbiddenRoles = emptyMap(),
            lastFailedThreats = emptyList(),
            openThreats = randomGenerator.randomElements(room.possibleThreats, numberOfThreats),
            roundEndingTime = clock.time().plusMillis(TimeUnit.SECONDS.toMillis(10)),
            currentRoundState = COMMUNICATION_PHASE,
            currentRoundNumber = 0,
            maxRoundNumber = 5 + randomGenerator.nextInt(10)
        )

        synchronized(rooms) {
          rooms[roomName] = newRoom
        }
      }
    }

    return newRoom.asRoomInformation()
  }

  @GET
  @Path("/create-room")
  @Operation(operationId = "createRoom")
  fun createRoom(
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("possibleThreats") possibleThreats: String,
    @QueryParam("seed") seed: Long?
  ): Room {
    val encodedPossibleThreats = possibleThreats.split(',')
        .map { RoleThreat(it.trim()) }
    if (encodedPossibleThreats.size < 5) throw ClientErrorException("Not allowed to create a room with less than 5 possible threats", 422)

    // For the Game Jam we will assume we won't clash and override a room with the same name.
    val randomGenerator: RandomGenerator = DefaultRandomGenerator(seed)
    val roomName = RoomName(randomGenerator.generateRoomName())
    randomGenerators[roomName] = randomGenerator

    val room = RoomState.Room(listOf(playerId), encodedPossibleThreats, roomName, playerId)

    synchronized(rooms) {
      rooms[roomName] = room
    }

    return room
  }

  @GET
  @Path("/join-room")
  @Operation(operationId = "joinRoom")
  fun joinRoom(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId
  ): RoomInformation {
    val room = getRoom(roomName) { if (it.players.contains(playerId)) throw ClientErrorException("You are already part of the room with the name: ${roomName.name}", 422) }
    val newRoom: RoomState

    synchronized(rooms) {
      newRoom = room.copyJoining(playerId)
      rooms[roomName] = newRoom
    }

    return newRoom.asRoomInformation()
  }

  @GET
  @Path("/room-information")
  @Operation(operationId = "roomInformation")
  fun roomInformation(
    @NotNull @QueryParam("roomName") roomName: RoomName
  ): RoomInformation {
    return getRoom(roomName).asRoomInformation()
  }

  @GET
  @Path("/send-emojis")
  @Operation(operationId = "sendEmojis")
  fun sendEmojis(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("emojis") emojis: String
  ) {
    getRoom(roomName) { if (!it.players.contains(playerId)) throw ClientErrorException("You are not part of the room with the name: ${roomName.name}", 422) }

    val encodedEmojis = emojis.split(',').map { it.trim() }

    if (encodedEmojis.isEmpty()) throw ClientErrorException("You didn't give me any Emojis :(", 422)
  }

  @GET
  @Path("/set-role")
  @Operation(operationId = "setRole")
  fun setRole(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("role") role: String
  ) {
    getRoom(roomName) { if (!it.players.contains(playerId)) throw ClientErrorException("You are not part of the room with the name: ${roomName.name}", 422) }
  }

  private inline fun getRoom(roomName: RoomName, validation: (RoomState) -> Unit = { }): RoomState {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw NotFoundException("Can't find a room with the name: ${roomName.name}")

      validation.invoke(room)
    }

    return room
  }
}
