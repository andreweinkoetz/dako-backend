package edu.hm.dako.chat.server;

import java.io.IOException;
import javax.websocket.Session;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientListEntry;

public interface WebSocketServerInterface {

	public void open(Session session);
	
	public void handleIncomingPDU(ChatPDU receivedPdu) throws IOException;
	
	public void error(Throwable t);
	
	public void close();
	
	public boolean checkIfClientIsDeletable();
	
	public void loginRequestAction(ChatPDU receivedPdu);
	
	public void logoutRequestAction(ChatPDU receivedPdu);
	
	public void sendLogoutResponse(String eventInitiatorClient);
	
	public void chatMessageRequestAction(ChatPDU receivedPdu);
	
	public void sendPduToClient(ClientListEntry client, ChatPDU pdu);
	
}
