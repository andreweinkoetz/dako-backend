package edu.hm.dako.chat.server;

import java.io.IOException;
import javax.websocket.Session;

import edu.hm.dako.chat.common.ChatPDU;

public interface WebSocketServerInterface {

	public void open(Session session);
	
//	public void messageReceived(String message) throws IOException;
	
	public void pduReceived(ChatPDU pdu) throws IOException;
	
	public void error(Throwable t);
	
	public void close();
}
