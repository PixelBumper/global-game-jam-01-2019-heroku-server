package com.ggj19.server.api

import com.ggj19.server.dtos.GameStateDTO
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Component
import java.lang.RuntimeException
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("/game")
@Produces(MediaType.APPLICATION_JSON)
@Component
@OpenAPIDefinition(
        info = Info(title = "Game Server", version = "1.0.0")
)
@Tag(name = "GameApi")
class GameApi {

    @GET
    fun helloWorld(

    ): String {
        if (true){
            throw RuntimeException()
        }
        return "Hello"
    }

    @POST
    @Path("/some-post")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    fun post(
            @FormParam("someParam")
            param:String
    ): GameStateDTO {
        return GameStateDTO(1)
    }
    @POST
    @Path("/some-post/{param}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    fun postQuery(
            @QueryParam("param")
            param:String
    ): GameStateDTO {
        return GameStateDTO(1)
    }

    @POST
    @Path("/some-post-body")
    fun postBody(
            gameStateDTO: GameStateDTO
    ): GameStateDTO {
        return gameStateDTO
    }



    @GET
    @Path("/gett-params")
    fun get(
            @QueryParam("test")
            test:String
    ): GameStateDTO {
        return GameStateDTO(1)
    }


}
