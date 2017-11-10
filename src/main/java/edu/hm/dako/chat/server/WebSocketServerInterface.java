package edu.hm.dako.chat.server;

import java.io.IOException;
import javax.websocket.Session;

public interface WebSocketServerInterface {

	public void open(Session session);
	
	public void messageReceived(String message) throws IOException;
	
	public void error(Throwable t);
	
	public void close();
}
