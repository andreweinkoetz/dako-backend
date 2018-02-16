package edu.hm.dako.chat.server;

import java.io.IOException;
import javax.websocket.Session;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientListEntry;

/**
 * Interface zur Repräsentation aller Methoden, die von jeder
 * Implementierungsform des WebSocket-Servers implementiert werden müssen.
 * 
 * @author Andre Weinkötz
 *
 */
public interface WebSocketServerInterface {

	
	/**
	 * Callback-Methode bei Verbindungsaufbau
	 * 
	 * @param session
	 */
	public void open(Session session);

	
	/**
	 * Callback-Methode beim Eintreffen einer neuen Nachricht
	 * 
	 * @param receivedPdu Zu verarbeitende Nachricht
	 * @throws IOException Exception bei Fehler in Verbindung
	 */
	public void handleIncomingPDU(ChatPDU receivedPdu) throws IOException;

	/**
	 * Callback-Methode bei Verbindungsfehler
	 * @param t Fehler
	 */
	public void error(Throwable t);

	/**
	 * Callback-Methode bei Verbindungsabbau
	 */
	public void close();

	/**
	 * Prüft, ob ein Client gelöscht werden kann.
	 * @return true wenn Client bereits beendet wurde
	 */
	public boolean checkIfClientIsDeletable();

	
	/**
	 * Methode zur Bearbeitung eines Login-Requests
	 * @param receivedPdu Zu verarbeitende Nachricht
	 */
	public void loginRequestAction(ChatPDU receivedPdu);

	/**
	 * Methode zur Bearbeitung eines Logout-Requests
	 * @param receivedPdu Zu verarbeitende Nachricht
	 */
	public void logoutRequestAction(ChatPDU receivedPdu);

	
	/**
	 * Sendet eine Logout-Response-PDU
	 * @param eventInitiatorClient Client, der den Verbindungsabbau angefragt hat
	 */
	public void sendLogoutResponse(String eventInitiatorClient);

	/**
	 * Methode zur Bearbeitung eines Message-Requests
	 * @param receivedPdu Zu verarbeitende Nachricht
	 */
	public void chatMessageRequestAction(ChatPDU receivedPdu);

	/**
	 * Methode zum Senden einer PDU an einen Client
	 * 
	 * @param client Empfänger
	 * @param pdu Nachricht
	 */
	public void sendPduToClient(ClientListEntry client, ChatPDU pdu);

}
