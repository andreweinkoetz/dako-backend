package edu.hm.dako.chat.server;

public abstract class AbstractWebSocketServer implements WebSocketServerInterface {

	// Gemeinsam fuer alle Workerthreads verwaltete Liste aller eingeloggten
	// Clients
	protected SharedChatClientList clients = SharedChatClientList.getInstance();

	// Zaehler fuer Test
	protected SharedServerCounter counter;
	
	protected String userName;
	
	protected Boolean finished;

	
}
