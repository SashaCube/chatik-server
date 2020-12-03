// Global variable to jold the websocket.
var socket = null;

/**
 * This function is in cahrge of connecting the client
 */
function connect(){
    // First we create the socket
    // The socket will be connected automatically asap. 
    // Not now but after returning to the event loop,
    // so we can register handlers safely before the connection is performed
    console.log("Begin connect");
    socket = new WebSocket("ws://" + window.location.host + "/ws");

    // All errors will be reported to console
    socket.onerror = function() {
        console.log("socket error");
    };

    // Handle opening of connection
    socket.onopen = function() {
        write("Connected");
    };

    // Handle closing of connection
    socket.onclose = function(ent) {

        var explanation = "";
        if(evt.reason && evt.reason.length > 0){
            explanation = "reason: " + evt.reason;
        } else {
            explanation = "without a reason specified";
        }

        write("Disconnected with close code " + evt.code + " and " + explanation);
        setTimeout(connect, 500);
    };

    // Handle message from server
    socket.onmessage = function(event) {
        write(event.data.toString());
    }
}

/**
 * Handle messages received from the server.
 * 
 * @param message The textual message
 */
function received(message){
    write(message);
}

/**
 * Writes a message in the HTML 'messages' container that the user can see.
 * 
 * @param message The message to wright in the container
 */
function write(message){

    // Create an HTML paragraph and sets its class and contents.
    var line = document.createElement("p");
    line.className = "message";
    line.textContent = message;

    // Add text and sctoll to the top
    var messagesDiv = document.getElementById("messages");
    messagesDiv.appendChild(line);
    messagesDiv.scrollTop = line.offsetTop;
}

/**
 * Function in charge of sending the 'commandInput' text to the server via the socket.
 */
function onSend() {
    console.log("onSend")
    var input = document.getElementById("commandInput");

    // Validate that the input exists
    if(input) {
        console.log("input true")
        var text = input.value;

        if(text && socket) {
            socket.send(text);
            input.value = "";
        }
    } else {
        console.log("input false")
    }
}

/**
 * The initial code to be executed once the page has been loaded anbd is ready.
 */
function start(){
    connect();

    document.getElementById("sendButton").onclick = function() {
        onSend();
    }
    // If we pressed the 'enter' key being inside the 'commandInput',
    // send the message to improve accessibility and making it nicer.
    document.getElementById("commandInput").onkeydown = function(e) {
        if(e.keyCode == 13){
            onSend();
        }
    };
}

/**
 * The entry point of the client.
 */
function initLoop(){
    // Is the sendButton available already? If so, start. If not, wait a bit and return this.
    if(document.getElementById("sendButton")){
        start();
    } else {
        setTimeout(initLoop, 300);
    }
}

// This is the entry point of the client.
initLoop();