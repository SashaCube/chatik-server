package com.chatik.server

import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class in charge of the logic of the chat server.
 * It contains handlers to events and commands to send messages to specific user in the server.
 */
class ChatServer {

    val userCounter = AtomicInteger()

    /**
     * A concurrent map associating session IDs to user names.
     */
    val memberNames = ConcurrentHashMap<String, String>()

    /**
     * Associates a session-id to a set of websockets
     * Since a browser is able to open several tabs and windows with the same cookies and thus the same session.
     * There might be several opened sockets for the same client.
     */
    val members = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    /**
     * A list of the latest messages sent to the server, so new members can have a bit context of what
     * other people was talking about before joining.
     */
    val lastMessages = LinkedList<String>()

    /**
     * Handles that a member identified with a session id and a socket joined.
     */
    suspend fun memberJoin(member: String, socket: WebSocketSession) {

        // Checks if this user is already registered in the server and gives him/her a temporal name is required.
        val name = memberNames.computeIfAbsent(member) { "user${userCounter.incrementAndGet()}" }

        // Associates this socket to the member id.
        // Since iteration is likely to happen more frequently that adding new items,
        // we use a `CopyOnWrightArrayList`.
        // We could also control how many sockets we would allow per client here before appending it.
        val list = members.computeIfAbsent(member) { CopyOnWriteArrayList<WebSocketSession>() }
        list.add(socket)

        // Only when joining the first socket for a member notifies the rest of the users.
        if (list.size == 1) {
            broadcast("server", "Member joined: $name.")
        }

        // Sends teh user the latest messages from this server to let the member have a bit context.
        val messages = synchronized(lastMessages) { lastMessages.toList() }
        for (message in messages) {
            socket.send(Frame.Text(message))
        }
    }

    /**
     * Handles a [member] identified by its session id renaming [to] a specific name.
     */
    suspend fun memberRenamed(member: String, to: String){
        // Re-sets the member name.
        val oldName = memberNames.put(member, to) ?: member
        // Notifies everyone in the server about his change.
        broadcast("sever", "Member renamed from $oldName to $to")
    }

    /**
     * Handles that a [member] with a specific [socket] left the server.
     */
    suspend fun memberLeft(member: String, socket: WebSocketSession) {
        // Removes the socket connection for this member
        val connections = members[member]
        connections?.remove(socket)

        // If no more sockets are connected for this member, let's remove it from the server
        // and notify the rest of the users about this event.
        if(connections != null && connections.isEmpty()){
            val name = memberNames.remove(member) ?: member
            broadcast("server", "Member left: $name.")
        }
    }

    /**
     * Handles that 'who' command by sending the member a list of all all members names int he server
     */
    suspend fun who(sender: String){
        members[sender]?.send(Frame.Text(memberNames.values.joinToString(prefix = "[server::who] ")))
    }

    /**
     * Handles the 'help' command by sending the member a list of available commands.
     */
    suspend fun help(sender: String){
        members[sender]?.send(Frame.Text("[server::help] Possible commands are /user, /help and /who"))
    }

    /**
     * Handles sending to a [recipient] form a [sender] a [message].
     *
     * Both [recipient] and [sender] are identified by its session-id.
     */
    suspend fun sendTo(recipient: String, sender: String, message: String){
        members[recipient]?.send(Frame.Text("[$sender] $message"))
    }

    suspend fun message(sender: String, message: String){
        val name = memberNames[sender] ?: sender
        val formatted = "[$name] $message"

        broadcast(formatted)

        // Appends the message to the list of [lastMessages] and caps that collection to 100 items
        synchronized(lastMessages){
            lastMessages.add(formatted)
            if(lastMessages.size > 100){
                lastMessages.removeFirst()
            }
        }
    }

    /**
     * Sends a [message] to all members in the server, including all the connections per member
     */
    suspend fun broadcast(message: String) {
        members.values.forEach { socket ->
            socket.send(Frame.Text(message))
        }
    }

    /**
     * Sends a [message] coming from [sender] to all the members in the server,
     * including all the connections per member
     */
    suspend fun broadcast(sender: String, message: String) {
        val name = memberNames[sender] ?: sender
        broadcast("[$name] $message")
    }

    /**
     * Sends a [frame] to a list [this] [WebSocketSession]
     */
    suspend fun List<WebSocketSession>.send(frame: Frame) {
        forEach {
            try {
                it.send(frame.copy())
            } catch (t: Throwable) {
                try {
                    it.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, ""))
                } catch (ignore: ClosedSendChannelException) {
                }
            }
        }
    }
}