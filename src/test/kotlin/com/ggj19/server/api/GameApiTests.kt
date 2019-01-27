package com.ggj19.server.api

import com.ggj19.server.clock.TestingClock
import com.ggj19.server.dtos.Emoji
import com.ggj19.server.dtos.Phase.PHASE_DOOMED
import com.ggj19.server.dtos.Phase.PHASE_EMOJIS
import com.ggj19.server.dtos.Phase.PHASE_ROLE
import com.ggj19.server.dtos.PlayerId
import com.ggj19.server.dtos.RoleThreat
import com.ggj19.server.dtos.RoomInformation
import com.ggj19.server.dtos.RoomName
import com.ggj19.server.dtos.RoomState.Playing
import com.ggj19.server.dtos.RoomState.Room
import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.time.Instant
import javax.ws.rs.ClientErrorException
import javax.ws.rs.NotFoundException

class GameApiTests {
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

  @Test fun roomInformationTransitioning() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.startRoom(room.name, player1)

    val information = gameApi.sendEmojis(room.name, player1, "eggplant")

    clock.time = clock.time.plusSeconds(room.roundLengthInSeconds) // Simulate that Emoji phase is over.
    assertThat(gameApi.roomInformation(room.name)).isEqualTo(RoomInformation(null, information.playing!!.copy(
        version = 3,
        currentTime = clock.time.toEpochMilli(),
        roundEndingTime = clock.time.plusSeconds(10).toEpochMilli(),
        currentPhase = PHASE_ROLE
    )))

    gameApi.setRole(room.name, player1, possibleThreatLazy)

    clock.time = clock.time.plusSeconds(room.roundLengthInSeconds)
    assertThat(gameApi.roomInformation(room.name)).isEqualTo(RoomInformation(null, information.playing!!.copy(
        version = 5,
        forbiddenRoles = mapOf(player1 to possibleThreatLazy),
        playedPlayerRoles = emptyMap(),
        playerEmojis = emptyMap(),
        playerEmojisHistory = mapOf(player1 to listOf(listOf(Emoji("eggplant")))),
        lastFailedThreats = emptyList(),
        openThreats = listOf(possibleThreatShooter),
        currentTime = clock.time.toEpochMilli(),
        roundEndingTime = clock.time.plusSeconds(10).toEpochMilli(),
        currentPhase = PHASE_EMOJIS,
        currentRoundNumber = 1
    )))
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
        maximumThreats = room.maximumThreats,
        version = 1,
        roundLengthInSeconds = 10L,
        forbiddenRoles = emptyMap(),
        playedPlayerRoles = emptyMap(),
        playerEmojis = emptyMap(),
        playerEmojisHistory = emptyMap(),
        lastFailedThreats = emptyList(),
        openThreats = listOf(possibleThreatLazy),
        currentTime = clock.time().toEpochMilli(),
        roundEndingTime = clock.time().plusSeconds(room.roundLengthInSeconds).toEpochMilli(),
        currentPhase = PHASE_EMOJIS,
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
        maximumThreats = room.maximumThreats,
        version = 1,
        roundLengthInSeconds = 10L,
        forbiddenRoles = emptyMap(),
        playedPlayerRoles = emptyMap(),
        playerEmojis = emptyMap(),
        playerEmojisHistory = emptyMap(),
        lastFailedThreats = emptyList(),
        openThreats = listOf(possibleThreatLazy, possibleThreatShooter, possibleThreatMusician),
        currentTime = clock.time().toEpochMilli(),
        roundEndingTime = clock.time().plusSeconds(room.roundLengthInSeconds).toEpochMilli(),
        currentPhase = PHASE_EMOJIS,
        currentRoundNumber = 0,
        maxRoundNumber = 9
    )))
  }

  @Test fun sendEmojisNotStarted() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThrows<ClientErrorException> {
      gameApi.sendEmojis(room.name, player2, "eggplant")
    }.hasMessage("Game hasn't started")
  }

  @Test fun sendEmojisWrongRoom() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.startRoom(room.name, player1)

    assertThrows<ClientErrorException> {
      gameApi.sendEmojis(room.name, player2, "eggplant")
    }.hasMessage("You are not part of the room with the name: ${room.name.name}")
  }

  @Test fun sendEmojisTooFew() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.startRoom(room.name, player1)

    assertThrows<ClientErrorException> {
      gameApi.sendEmojis(room.name, player1, "")
    }.hasMessage("You must send between one and two Emojis :(")
  }

  @Test fun sendEmojisTooMany() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.startRoom(room.name, player1)

    assertThrows<ClientErrorException> {
      gameApi.sendEmojis(room.name, player1, "eggplant,apple,foot")
    }.hasMessage("You must send between one and two Emojis :(")
  }

  @Test fun sendEmojis() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.joinRoom(room.name, player2)
    val information = gameApi.startRoom(room.name, player1)

    assertThat(gameApi.sendEmojis(room.name, player1, "eggplant")).isEqualTo(information.copy(playing = information.playing!!.copy(
        version = 2,
        playerEmojis = mapOf(player1 to listOf(Emoji("eggplant")))
    )))

    assertThat(gameApi.sendEmojis(room.name, player2, "eggplant,apple")).isEqualTo(information.copy(playing = information.playing!!.copy(
        version = 3,
        playerEmojis = mapOf(player1 to listOf(Emoji("eggplant")), player2 to listOf(Emoji("eggplant"), Emoji("apple")))
    )))
  }

  @Test fun sendEmojisWrongPhase() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.joinRoom(room.name, player2)
    gameApi.startRoom(room.name, player1)
    gameApi.changePhase(room.name, PHASE_ROLE)

    assertThrows<ClientErrorException> {
      gameApi.sendEmojis(room.name, player1, "Game is not in emoji phase")
    }
    gameApi.changePhase(room.name, PHASE_DOOMED)

    assertThrows<ClientErrorException> {
      gameApi.sendEmojis(room.name, player1, "Game is not in emoji phase")
    }
  }

  @Test fun setRoleNotStarted() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")

    assertThrows<ClientErrorException> {
      gameApi.setRole(room.name, player2, possibleThreatShooter)
    }.hasMessage("Game hasn't started")
  }

  @Test fun setRoleWrongRoom() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.startRoom(room.name, player1)

    assertThrows<ClientErrorException> {
      gameApi.setRole(room.name, player2, possibleThreatShooter)
    }.hasMessage("You are not part of the room with the name: ${room.name.name}")
  }

  @Test fun setRole() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.joinRoom(room.name, player2)
    gameApi.startRoom(room.name, player1)
    val information = gameApi.changePhase(room.name, PHASE_ROLE)

    assertThat(gameApi.setRole(room.name, player1, possibleThreatShooter)).isEqualTo(information.copy(playing = information.playing!!.copy(
        version = 2,
        playedPlayerRoles = mapOf(player1 to possibleThreatShooter)
    )))

    assertThat(gameApi.setRole(room.name, player2, possibleThreatLazy)).isEqualTo(information.copy(playing = information.playing!!.copy(
        version = 3,
        playedPlayerRoles = mapOf(player1 to possibleThreatShooter, player2 to possibleThreatLazy)
    )))
  }

  @Test fun setRoleWrongPhase() {
    val room = createRoom("SHOOTER, ENGINEER, PILOT, LAZY, MUSICIAN")
    gameApi.joinRoom(room.name, player2)
    gameApi.startRoom(room.name, player1)

    gameApi.changePhase(room.name, PHASE_EMOJIS)

    assertThrows<ClientErrorException> {
      gameApi.setRole(room.name, player1, possibleThreatShooter)
    }.hasMessage("Game is not in role phase")

    gameApi.changePhase(room.name, PHASE_DOOMED)

    assertThrows<ClientErrorException> {
      gameApi.setRole(room.name, player1, possibleThreatShooter)
    }.hasMessage("Game is not in role phase")
  }

  private fun createRoom(possibleThreats: String): Room {
    val room = gameApi.createRoom(player1, possibleThreats, roundLengthInSeconds = 10L, numberOfRounds = 9, seed = 30L, maximumThreats = 4)

    assertThat(room).isEqualTo(Room(
        listOf(player1),
        listOf(
            possibleThreatShooter, possibleThreatEngineer, possibleThreatPilot,
            possibleThreatLazy, possibleThreatMusician
        ),
        RoomName("Dariana"),
        player1,
        10L,
        9,
        4
    ))

    return room
  }
}
