package edu.hm.dako.chat.server;

import javax.websocket.Session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientListEntry;

public abstract class AbstractWebSocketServer implements WebSocketServerInterface {

	private static Log log = LogFactory.getLog(AbstractWebSocketServer.class);

	protected Session session;

	// Gemeinsam fuer alle Workerthreads verwaltete Liste aller eingeloggten
	// Clients
	protected SharedChatClientList clients = SharedChatClientList.getInstance();

	// Zaehler fuer Test
	protected SharedServerCounter counter;

	protected String userName;

	protected Boolean finished;

	@Override
	public void sendLogoutResponse(String eventInitiatorClient) {
		ClientListEntry client = clients.getClient(eventInitiatorClient);

		if (client != null) {
			ChatPDU responsePdu = ChatPDU.createLogoutResponsePdu(eventInitiatorClient, 0, 0, 0, 0,
					client.getNumberOfReceivedChatMessages());

			log.debug(eventInitiatorClient + ": SentEvents aus Clientliste: " + client.getNumberOfSentEvents()
					+ ": ReceivedConfirms aus Clientliste: " + client.getNumberOfReceivedEventConfirms());
			try {
				sendPduToClient(clients.getClient(eventInitiatorClient), responsePdu);
			} catch (Exception e) {
				log.debug("Senden einer Logout-Response-PDU an " + eventInitiatorClient + " fehlgeschlagen");
				log.debug("Exception Message: " + e.getMessage());
			}

			log.debug("Logout-Response-PDU an Client " + eventInitiatorClient + " gesendet");
		}

	}

}
