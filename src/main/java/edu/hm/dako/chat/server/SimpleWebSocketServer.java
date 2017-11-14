package edu.hm.dako.chat.server;

import java.io.IOException;
import java.util.ArrayList;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import edu.hm.dako.chat.common.ChatPDUEncoder;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ChatPDUDecoder;


@ServerEndpoint(value = "/simplechat", encoders = { ChatPDUEncoder.class }, decoders = { ChatPDUDecoder.class })
public class SimpleWebSocketServer implements WebSocketServerInterface {

	static ArrayList<Session> sess = new ArrayList<Session>();
	Session session;
	
	@Override
	@OnOpen
	public void open(Session session) {
		this.session = session;
		sess.add(session);
	}

//	@Override
//	@OnMessage
//	public void messageReceived(String message) throws IOException {
//		ChatPDU pdu = new ChatPDU();
//		pdu.setMessage("NonSense");
//		session.getAsyncRemote().sendObject(pdu);
//
//	}

	@Override
	@OnError
	public void error(Throwable t) {
		
	}

	@Override
	@OnClose
	public void close() {

	}

	@Override
	@OnMessage
	public void pduReceived(ChatPDU pdu) throws IOException {
		if(pdu.getClientStatus() == ClientConversationStatus.REGISTERED) {
		ChatPDU response = ChatPDU.createChatMessageEventPdu(pdu.getUserName(), pdu);
		session.getAsyncRemote().sendObject(response);
		}
		
	}
	
	
	
	

}
