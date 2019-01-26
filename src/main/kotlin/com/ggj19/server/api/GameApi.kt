package com.ggj19.server.api

import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoleThreat
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import com.ggj19.server.dtos.RoundState.COMMUNICATION_PHASE
import com.ggj19.server.random
import com.github.javafaker.Faker
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.validation.constraints.NotNull
import javax.ws.rs.GET
import javax.ws.rs.NotAllowedException
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
class GameApi {
  private val faker = Faker()
  private val rooms = HashMap<RoomName, RoomState>()
  private val randomGenerator = ConcurrentHashMap<RoomName, Random>()

  @GET
  @Path("/start-room")
  @Operation(operationId = "startRoom")
  fun startRoom(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId
  ): RoomInformation {
    val room = getRoom(roomName)

    when (room) {
      is Playing -> throw NotAllowedException("Game has already started")
      is Room -> {
        if (room.owner != playerId) {
          throw NotAllowedException("You're not the owner of the room and hence can't start the room")
        }

        val random = randomGenerator.getValue(roomName)

        synchronized(rooms) {
          rooms[roomName] = Playing(
              players = room.players,
              possibleThreats = room.possibleThreats,
              forbiddenRoles = emptyMap(),
              lastFailedThreats = emptyList(),
              openThreats = room.possibleThreats.random(random, minOf(minOf(minOf(1, room.players.size * 2 / 3), room.players.size), room.possibleThreats.size)),
              roundEndingTime = Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(10)),
              currentRoundState = COMMUNICATION_PHASE,
              currentRoundNumber = 0,
              maxRoundNumber = 5 + random.nextInt(10)
          )
        }
      }
    }

    return room.asRoomInformation()
  }

  @GET
  @Path("/create-room")
  @Operation(operationId = "createRoom")
  fun createRoom(
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("possibleThreats") possibleThreats: String,
    @QueryParam("seed") seed: Long?
  ): Room {
    // For the Game Jam we will assume we won't clash and override a room with the same name.
    val roomName = RoomName(faker.name().firstName())
    val encodedPossibleThreats = possibleThreats.split(',')
        .map { RoleThreat(it.trim()) }

    if (encodedPossibleThreats.size < 5) throw NotAllowedException("Not allowed to create a room with less than 5 possible threats")

    val room = RoomState.Room(listOf(playerId), encodedPossibleThreats, roomName, playerId)

    synchronized(rooms) {
      rooms[roomName] = room
    }

    randomGenerator[roomName] = Random().apply { if (seed != null) setSeed(seed) }

    return room
  }

  @GET
  @Path("/join-room")
  @Operation(operationId = "joinRoom")
  fun joinRoom(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId
  ): RoomInformation {
    val room = getRoom(roomName) { if (it.players.contains(playerId)) throw NotAllowedException("You are already part of the room with the name: $roomName") }

    synchronized(rooms) {
      rooms.put(roomName, room.copyJoining(playerId))
    }

    return room.asRoomInformation()
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
    getRoom(roomName) { if (!it.players.contains(playerId)) throw NotAllowedException("You are not part of the room with the name: $roomName") }

    val encodedEmojis = emojis.split(',').map { it.trim() }

    if (encodedEmojis.isEmpty()) throw NotAllowedException("You didn't give me any Emojis :(")
  }

  @GET
  @Path("/set-role")
  @Operation(operationId = "setRole")
  fun setRole(
    @NotNull @QueryParam("roomName") roomName: RoomName,
    @NotNull @QueryParam("playerId") playerId: PlayerId,
    @NotNull @QueryParam("role") role: String
  ) {
    getRoom(roomName) { if (!it.players.contains(playerId)) throw NotAllowedException("You are not part of the room with the name: $roomName") }
  }

  private inline fun getRoom(roomName: RoomName, validation: (RoomState) -> Unit = { }): RoomState {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw NotFoundException("Can't find a room with the name: $roomName")

      validation.invoke(room)
    }

    return room
  }
}
