package com.ggj19.server.api

import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoleThreat
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import com.ggj19.server.dtos.RoundState.COMMUNICATION_PHASE
import com.github.javafaker.Faker
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit
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
  // TODO(nik) add seed for randomness!

  private val faker = Faker()
  private val rooms = HashMap<RoomName, RoomState>()

  @GET
  @Path("/start-room")
  @Operation(operationId = "startRoom")
  fun startRoom(
    @QueryParam("playerId") playerId: PlayerId,
    @QueryParam("roomName") roomName: RoomName
  ): RoomInformation {
    val state: RoomState

    synchronized(rooms) {
      state = rooms[roomName] ?: throw NotFoundException("Can't find a room with the name: $roomName")
    }

    when (state) {
      is Playing -> throw NotAllowedException("Game has already started")
      is Room -> {
        if (state.owner != playerId) {
          throw NotAllowedException("You're not the owner of the room and hence can't start the room")
        }

        synchronized(rooms) {
          rooms[roomName] = Playing(
              players = state.players,
              possibleThreats = state.possibleThreats,
              forbiddenRoles = emptyMap(),
              lastFailedThreats = emptyList(),
              openThreats = listOf(state.possibleThreats.first()), // TODO(nik) generate this with randomness.
              roundEndingTime = Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(10)),
              currentRoundState = COMMUNICATION_PHASE,
              currentRoundNumber = 0,
              maxRoundNumber = 10 // TODO(nik) generate this with randomness.
          )
        }
      }
    }

    return state.asRoomInformation()
  }

  @GET
  @Path("/create-room")
  @Operation(operationId = "createRoom")
  fun createRoom(
    @QueryParam("playerId") playerId: PlayerId,
    @QueryParam("possibleThreats") possibleThreats: List<RoleThreat>
  ): Room {
    // For the Game Jam we will assume we won't clash and override a room with the same name.
    val roomName = RoomName(faker.name().firstName())
    val room = RoomState.Room(listOf(playerId), possibleThreats, roomName, playerId)

    synchronized(rooms) {
      rooms[roomName] = room
    }

    return room
  }

  @GET
  @Path("/join-room")
  @Operation(operationId = "joinRoom")
  fun joinRoom(
    @QueryParam("playerId") playerId: PlayerId,
    @QueryParam("roomName") roomName: RoomName
  ): RoomInformation {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw NotFoundException("Can't find a room with the name: $roomName")

      if (room.players.contains(playerId)) {
        throw NotAllowedException("You are already part of the room with the name: $roomName")
      }

      rooms.put(roomName, room.copyJoining(playerId))
    }

    return room.asRoomInformation()
  }

  @GET
  @Path("/room-information")
  @Operation(operationId = "roomInformation")
  fun roomInformation(@QueryParam("roomName") roomName: RoomName): RoomInformation {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw NotFoundException("Can't find a room with the name: $roomName")
    }

    return room.asRoomInformation()
  }
}
