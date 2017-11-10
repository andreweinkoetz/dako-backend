package edu.hm.dako.chat.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import edu.hm.dako.chat.common.ChatPDUEncoder;
import edu.hm.dako.chat.common.ChatPDU;


@ServerEndpoint(value = "/simplechat", encoders = { ChatPDUEncoder.class })
public class SimpleWebSocketServer implements WebSocketServerInterface {

	static ArrayList<Session> sess = new ArrayList<Session>();
	Session session;
	
	@Override
	@OnOpen
	public void open(Session session) {
		this.session = session;
		sess.add(session);
	}

	@Override
	@OnMessage
	public void messageReceived(String message) throws IOException {
		
		ChatPDU pdu = new ChatPDU();
		pdu.setMessage("NonSense");
		session.getAsyncRemote().sendObject(pdu);

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
