package com.ggj19.server.mappers


import com.ggj19.server.dtos.ErrorMessageDTO
import org.springframework.stereotype.Component
import javax.validation.ConstraintViolationException
import javax.validation.ValidationException
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
@Component
class ValidationExceptionMapper : ExceptionMapper<ValidationException> {
    override fun toResponse(exception: ValidationException?): Response {
        return if (exception is ConstraintViolationException) {
            val message = exception.constraintViolations.joinToString("\n") { it.message }
            Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(
                            ErrorMessageDTO(
                                    Response.Status.BAD_REQUEST.statusCode,
                                    message)).build()
        } else {
            INTERNAL_SERVER_ERROR
        }
    }
}
