package edu.hm.dako.chat.server;

import java.io.IOException;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;


@ServerEndpoint("/simplechat")
public class SimpleWebSocketServer implements WebSocketServerInterface {

	Session session;
	
	@Override
	@OnOpen
	public void open(Session session) {
		this.session = session;
	}

	@Override
	@OnMessage
	public void messageReceived(String message) throws IOException {
		session.getAsyncRemote().sendText("Hallo Simple!");
	}

	@Override
	@OnError
	public void error(Throwable t) {
		
	}

	@Override
	@OnClose
	public void close() {

	}
	
	
	
	

}
