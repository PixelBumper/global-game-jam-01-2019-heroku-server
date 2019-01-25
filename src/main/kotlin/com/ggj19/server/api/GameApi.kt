package com.ggj19.server.api

import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import com.ggj19.server.dtos.asRoomInformation
import com.github.javafaker.Faker
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Component
import javax.ws.rs.Consumes
import javax.ws.rs.FormParam
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED

// TODO(nik) talk about errors with Carlo.
@Path("/game")
@Produces(MediaType.APPLICATION_JSON)
@Component
@OpenAPIDefinition(info = Info(title = "Game Server", version = "1.0.0"))
@Tag(name = "GameApi")
class GameApi {
  private val faker = Faker()
  private val rooms = HashMap<RoomName, RoomState>()

  @POST @Path("/start-room") @Consumes(APPLICATION_FORM_URLENCODED) fun startRoom(
    @FormParam("playerId") playerId: PlayerId,
    @FormParam("roomName") roomName: RoomName
  ): RoomInformation {
    val state: RoomState

    synchronized(rooms) {
      state = rooms[roomName] ?: throw IllegalArgumentException("Can't find a room with the name: $roomName")
    }

    when (state) {
      is Room -> {
        require(state.owner != playerId) { "You're not the owner of the room and hence can't start the room" }

        synchronized(rooms) {
          rooms[roomName] = Playing(state.players)
        }
      }
      is Playing -> {
        throw IllegalArgumentException("Game has already started")
      }
    }

    return state.asRoomInformation()
  }

  @POST @Path("/create-room") @Consumes(APPLICATION_FORM_URLENCODED) fun createRoom(
    @FormParam("playerId") playerId: PlayerId
  ): Room {
    val roomName = RoomName(faker.name().fullName()) // For the Game Jam we will assume we won't clash and override a room with the same name.
    val room = RoomState.Room(roomName, playerId, listOf(playerId))

    synchronized(rooms) {
      rooms[roomName] = room
    }

    return room
  }

  @POST @Path("/join-room") @Consumes(APPLICATION_FORM_URLENCODED) fun joinRoom(
    @FormParam("playerId") playerId: PlayerId,
    @FormParam("roomName") roomName: RoomName
  ): RoomInformation {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw IllegalArgumentException("Can't find a room with the name: $roomName")

      if (room.players.contains(playerId)) {
        throw IllegalArgumentException("You are already part of the room with the name: $roomName")
      }

      rooms.put(roomName, room.copyJoining(playerId))
    }

    return room.asRoomInformation()
  }

  @GET @Path("/room-information") fun roomInformation(@QueryParam("roomName") roomName: RoomName): RoomInformation {
    val room: RoomState

    synchronized(rooms) {
      room = rooms[roomName] ?: throw IllegalArgumentException("Can't find a room with the name: $roomName")
    }

    return room.asRoomInformation()
  }
}
