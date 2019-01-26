package com.ggj19.server.api

import com.ggj19.server.clock.TestingClock
import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoleThreat
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import com.ggj19.server.dtos.RoundState.COMMUNICATION_PHASE
import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.ws.rs.ClientErrorException
import javax.ws.rs.NotFoundException

@SpringBootTest @RunWith(SpringRunner::class) class GameApiTests {
  private lateinit var clock: TestingClock
  private lateinit var gameApi: GameApi

  private val possibleThreatShooter = RoleThreat("SHOOTER")
  private val possibleThreatEngineer = RoleThreat("ENGINEER")
  private val possibleThreatPilot = RoleThreat("PILOT")
  private val possibleThreatLazy = RoleThreat("LAZY")
  private val possibleThreatMusician = RoleThreat("MUSICIAN")

  private val player1 = PlayerId("Player 1")
  private val player2 = PlayerId("Player 2")
  private val player3 = PlayerId("Player 3")

  @Before fun setUp() {
    clock = TestingClock(Instant.ofEpochMilli(35345432))
    gameApi = GameApi(clock)
  }

  @Test fun createRoom() {
    createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
  }

  @Test fun roomInformationNotPresent() {
    assertThrows<NotFoundException> {
      gameApi.roomInformation(RoomName("Not existing"))
    }.hasMessage("Can't find a room with the name: Not existing")
  }

  @Test fun roomInformation() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThat(gameApi.roomInformation(room.name)).isEqualTo(RoomInformation(room, null))
  }

  @Test fun joinCreatedRoom() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThat(gameApi.joinRoom(room.name, player2)).isEqualTo(RoomInformation(room.copy(players = room.players.plus(player2)), null))
    assertThat(gameApi.joinRoom(room.name, player3)).isEqualTo(RoomInformation(room.copy(players = room.players.plus(player2).plus(player3)), null))
  }

  @Test fun doubleJoinCreatedRoom() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThat(gameApi.joinRoom(room.name, player2)).isEqualTo(RoomInformation(room.copy(players = room.players.plus(player2)), null))
    assertThrows<ClientErrorException> {
      gameApi.joinRoom(room.name, player2)
    }.hasMessage("You are already part of the room with the name: ${room.name.name}")
  }

  @Test fun createRoomTooFewThreats() {
    assertThrows<ClientErrorException> {
      createRoom("SHOOTER, ENGINEER, PILOT, LAZY")
    }.hasMessage("Not allowed to create a room with less than 5 possible threats")
  }

  @Test fun startRoomDifferentOwner() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThrows<ClientErrorException> {
      gameApi.startRoom(room.name, player2)
    }.hasMessage("You're not the owner of the room and hence can't start the room")
  }

  @Test fun startRoom() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThat(gameApi.startRoom(room.name, player1)).isEqualTo(RoomInformation(null, Playing(
        players = room.players,
        possibleThreats = room.possibleThreats,
        forbiddenRoles = emptyMap(),
        lastFailedThreats = emptyList(),
        openThreats = listOf(possibleThreatShooter),
        roundEndingTime = clock.time().plusMillis(TimeUnit.SECONDS.toMillis(10)),
        currentRoundState = COMMUNICATION_PHASE,
        currentRoundNumber = 0,
        maxRoundNumber = 9
    )))
  }

  @Test fun startRoomAlreadyStarted() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    gameApi.startRoom(room.name, player1)
    assertThrows<ClientErrorException> {
      gameApi.startRoom(room.name, player1)
    }.hasMessage("Game has already started")
  }

  @Test fun startRoomMultiplePlayers() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThat(gameApi.joinRoom(room.name, player2)).isEqualTo(RoomInformation(room.copy(players = room.players.plus(player2)), null))
    assertThat(gameApi.joinRoom(room.name, player3)).isEqualTo(RoomInformation(room.copy(players = room.players.plus(player2).plus(player3)), null))
    assertThat(gameApi.startRoom(room.name, player1)).isEqualTo(RoomInformation(null, Playing(
        players = room.players.plus(player2).plus(player3),
        possibleThreats = room.possibleThreats,
        forbiddenRoles = emptyMap(),
        lastFailedThreats = emptyList(),
        openThreats = listOf(possibleThreatShooter),
        roundEndingTime = clock.time().plusMillis(TimeUnit.SECONDS.toMillis(10)),
        currentRoundState = COMMUNICATION_PHASE,
        currentRoundNumber = 0,
        maxRoundNumber = 9
    )))
  }

  // TODO(nik) sendEmojis
  // TODO(nik) setRole

  // TODO(us) should we allow players joining while the game has already started?

  private fun createRoom(possibleThreats: String): Room {
    val room = gameApi.createRoom(player1, possibleThreats, seed = 30L)

    assertThat(room).isEqualTo(Room(
        listOf(player1),
        listOf(
            possibleThreatShooter, possibleThreatEngineer, possibleThreatPilot,
            possibleThreatLazy, possibleThreatMusician
        ),
        RoomName("Jim Laucher"),
        player1
    ))

    return room
  }
}
