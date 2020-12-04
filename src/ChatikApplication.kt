package com.chatik.server

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import java.time.Duration

@KtorExperimentalAPI
@ExperimentalCoroutinesApi
fun Application.main() {
    ChatikApplication().apply { main() }
}

/**
 * In this case we have a class holding our application state so it is not global and can be tested easier.
 */
class ChatikApplication {

    /**
     * This class handles the logic of a [ChatServer].
     * With the standard handlers [ChatServer.memberJoin] or [ChatServer.memberLeft] and operations like
     * sending messages to everyone or to specific people connected to the server.
     */
    private val server = ChatServer()

    @ExperimentalCoroutinesApi
    @KtorExperimentalAPI
    fun Application.main() {
        /**
         * First we install the features we need. They are bound to the whole application.
         * Since this method has an implicit [Application] receiver that supports the [install] method.
         */
        install(DefaultHeaders)
        install(CallLogging)
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
        }
        install(Sessions) {
            cookie<ChatSession>("SESSION")
        }

        intercept(ApplicationCallPipeline.Features) {
            if (call.sessions.get<ChatSession>() == null) {
                call.sessions.set(ChatSession(generateNonce()))
            }
        }

        routing {

            // This defines a websocket route that allows a protocol upgrade to convert a HTTP request/response
            // into a bidirectional packetized connection.
            webSocket("/ws") {

                val session = call.sessions.get<ChatSession>()

                // we should always have session
                if (session == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                    return@webSocket
                }

                // Notify that a member joined
                server.memberJoin(session.id, this)

                try {

                    // We starts receiving messages (frames).
                    // Since this is a coroutine is suspended until receiving frames.
                    // Once the connection is closed, this consumeEach will finish and the code will continue.
                    incoming.consumeEach { frame ->
                        // Frames can be [Text], [Binary], [Ping], [Pong], [Close].
                        if (frame is Frame.Text) {
                            receivedMessage(session.id, frame.readText())
                        }
                    }
                } finally {
                    server.memberLeft(session.id, this)
                }
            }

            // This defines a block of static resources for the '/' path
            static {
                defaultResource("index.html", "web")
                resources("web")
            }

        }
    }

    /**
     * A chat session is identified by a unique nonce ID. This nonce comes from a secure random source.
     */
    data class ChatSession(val id: String)

    private suspend fun receivedMessage(id: String, command: String) {

        when {
            command.startsWith("/who") -> server.who(id)
            command.startsWith("/user") -> {
                val newName = command.removePrefix("/user").trim()

                when {
                    newName.isBlank() -> server.sendTo(id, "server::help", "/user [$newName]")
                    newName.length > 50 -> server.sendTo(
                        id,
                        "server::help",
                        "new name is too long: 50 characters limit"
                    )
                    else -> server.memberRenamed(id, newName)
                }
            }
            command.startsWith("/help") -> server.help(id)
            command.startsWith("/") -> server.sendTo(
                id,
                "server::help",
                "Unknown command ${command.takeWhile { !it.isWhitespace() }}"
            )

            // Handle a normal message
            else -> server.message(id, command)
        }
    }
}