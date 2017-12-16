package edu.hm.dako.chat.server;

import java.io.IOException;
import java.util.Vector;

import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.common.ChatPDUEncoder;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ClientListEntry;
import edu.hm.dako.chat.common.Pdutype;
import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ChatPDUDecoder;

@ServerEndpoint(value = "/simplechat", encoders = { ChatPDUEncoder.class }, decoders = { ChatPDUDecoder.class })
public class SimpleWebSocketServer extends AbstractWebSocketServer {

	private static Log log = LogFactory.getLog(SimpleWebSocketServer.class);

	@Override
	@OnOpen
	public void open(Session session) {
		this.session = session;
	}

	@Override
	@OnError
	public void error(Throwable t) {
		log.error("Kommunikation wurde unerwartet abgebrochen: " + t.getMessage());
	}

	@Override
	@OnClose
	public void close() {
		log.debug("Client meldet sich ab: " + clients.getClient(userName));
		if (removeClientOnClose()) {
			log.debug("Closed Session for " + userName);
			ChatPDU pdu = new ChatPDU();
			pdu.setPduType(Pdutype.LOGOUT_EVENT);
			pdu.setUserName(userName);
			pdu.setEventUserName(userName);
			pdu.setClients(clients.getClientNameList());
			sendLoginListUpdateEvent(pdu);
		}
	}

	private boolean removeClientOnClose() {
		ClientListEntry lostClient = clients.getClient(userName);
		if (lostClient == null) {
			return false;
		}
		if (lostClient.getStatus() != ClientConversationStatus.UNREGISTERING
				&& lostClient.getStatus() != ClientConversationStatus.UNREGISTERED) {
			clients.deleteClientWithoutCondition(userName);
			return true;
		}
		return false;
	}

	@Override
	@OnMessage
	public void handleIncomingPDU(ChatPDU receivedPdu) throws IOException {

		// Empfangene Nachricht bearbeiten
		try {
			switch (receivedPdu.getPduType()) {

			case LOGIN_REQUEST:
				// Login-Request vom Client empfangen
				loginRequestAction(receivedPdu);
				break;

			case CHAT_MESSAGE_REQUEST:
				// Chat-Nachricht angekommen, an alle verteilen
				chatMessageRequestAction(receivedPdu);
				break;

			case LOGOUT_REQUEST:
				// Logout-Request vom Client empfangen
				logoutRequestAction(receivedPdu);
				break;

			default:
				log.debug("Falsche PDU empfangen von Client: " + receivedPdu.getUserName() + ", PduType: "
						+ receivedPdu.getPduType());
				break;
			}
		} catch (Exception e) {
			log.error(e.getMessage());
		}

	}

	@Override
	public boolean checkIfClientIsDeletable() {
		ClientListEntry client;

		// Worker-Thread beenden, wenn sein Client schon abgemeldet ist
		if (userName != null) {
			client = clients.getClient(userName);
			if (client != null) {
				if (client.isFinished()) {
					// Loesche den Client aus der Clientliste
					// Ein Loeschen ist aber nur zulaessig, wenn der Client
					// nicht mehr in einer anderen Warteliste ist
					if (clients.deleteClient(userName) == true) {
						// Jetzt kann auch Worker-Thread beendet werden
						try {
							this.session.close();
						} catch (IOException e) {
							log.error(e.getMessage());
						}
						log.debug("Laenge der Clientliste nach dem Entfernen von " + userName + ": " + clients.size());
						log.debug("Worker-Thread fuer " + userName + " zum Beenden vorgemerkt");
						return true;
					}
				}
			}
		}

		// Garbage Collection in der Clientliste durchfuehren
		Vector<String> deletedClients = clients.gcClientList();
		if (deletedClients.contains(userName)) {
			log.debug("Ueber Garbage Collector ermittelt: Laufender Worker-Thread fuer " + userName
					+ " kann beendet werden");
			finished = true;
			return true;
		}
		return false;
	}

	@Override
	public void loginRequestAction(ChatPDU receivedPdu) {
		ChatPDU pdu;
		log.debug("Login-Request-PDU fuer " + receivedPdu.getUserName() + " empfangen");
		// Neuer Client moechte sich einloggen, Client in Client-Liste
		// eintragen
		if (!clients.existsClient(receivedPdu.getUserName())) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());

			ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), session);
			client.setLoginTime(System.nanoTime());

			clients.createClient(receivedPdu.getUserName(), client);
			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.REGISTERING);
			log.debug("User " + receivedPdu.getUserName() + " nun in Clientliste");

			userName = receivedPdu.getUserName();

			log.debug("Laenge der Clientliste: " + clients.size());

			// Login-Event an alle Clients (auch an den gerade aktuell
			// anfragenden) senden

			Vector<String> clientList = clients.getClientNameList();

			pdu = ChatPDU.createLoginEventPdu(userName, clientList, receivedPdu);
			sendLoginListUpdateEvent(pdu);

			// Login Response senden
			ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(userName, receivedPdu, clientList);

			try {
				sendPduToClient(client, responsePdu);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}

			log.debug("Login-Response-PDU an Client " + userName + " gesendet");

			// Zustand des Clients aendern
			clients.changeClientStatus(userName, ClientConversationStatus.REGISTERED);

		} else {
			// User bereits angemeldet, Fehlermeldung an Client senden,
			// Fehlercode an Client senden
			pdu = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

			try {
				session.getBasicRemote().sendObject(pdu);
				log.debug("Login-Response-PDU an " + receivedPdu.getUserName() + " mit Fehlercode "
						+ ChatPDU.LOGIN_ERROR + " gesendet");
			} catch (Exception e) {
				log.debug("Senden einer Login-Response-PDU an " + receivedPdu.getUserName() + " nicht moeglich");

			}
		}
	}

	@Override
	public void logoutRequestAction(ChatPDU receivedPdu) {
		ChatPDU pdu;

		log.debug("Logout-Request-PDU von " + receivedPdu.getUserName() + "empfangen");

		if (!clients.existsClient(userName)) {
			log.error("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {

			// Event an Client versenden
			Vector<String> clientList = clients.getClientNameList();
			pdu = ChatPDU.createLogoutEventPdu(userName, clientList, receivedPdu);

			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.UNREGISTERING);
			sendLoginListUpdateEvent(pdu);

			// Der Thread muss hier noch warten, bevor ein Logout-Response gesendet
			// wird, da sich sonst ein Client abmeldet, bevor er seinen letzten Event
			// empfangen hat. das funktioniert nicht bei einer grossen Anzahl an
			// Clients (kalkulierte Events stimmen dann nicht mit tatsaechlich
			// empfangenen Events ueberein.
			// In der Advanced-Variante wird noch ein Confirm gesendet, das ist
			// sicherer.

			try {
				Thread.sleep(1000);
			} catch (Exception e) {

			}

			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.UNREGISTERED);

			// Logout Response senden
			sendLogoutResponse(receivedPdu.getUserName());

			// Worker-Thread des Clients, der den Logout-Request gesendet
			// hat, auch gleich zum Beenden markieren
			clients.finish(receivedPdu.getUserName());

			log.debug("Laenge der Clientliste beim Vormerken zum Loeschen von " + receivedPdu.getUserName() + ": "
					+ clients.size());
		}

	}

	@Override
	public void chatMessageRequestAction(ChatPDU receivedPdu) {
		ClientListEntry client = null;

		clients.incrNumberOfReceivedChatMessages(receivedPdu.getUserName());

		log.debug("Chat-Message-Request-PDU von " + receivedPdu.getUserName() + " mit Sequenznummer "
				+ receivedPdu.getSequenceNumber() + " empfangen");

		if (!clients.existsClient(receivedPdu.getUserName())) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {
			// Liste der betroffenen Clients ermitteln
			Vector<String> sendList = clients.getClientNameList();
			ChatPDU pdu = ChatPDU.createChatMessageEventPdu(userName, receivedPdu);

			// Event an Clients senden
			for (String s : new Vector<String>(sendList)) {
				client = clients.getClient(s);
				try {
					if ((client != null) && (client.getStatus() != ClientConversationStatus.UNREGISTERED)) {
						pdu.setUserName(client.getUserName());
						sendPduToClient(client, pdu);
						log.debug("Chat-Event-PDU an " + client.getUserName() + " gesendet");
						clients.incrNumberOfSentChatEvents(client.getUserName());
					}
				} catch (Exception e) {
					log.debug("Senden einer Chat-Event-PDU an " + client.getUserName() + " nicht moeglich");

				}
			}

			client = clients.getClient(receivedPdu.getUserName());
			if (client != null) {
				ChatPDU responsePdu = ChatPDU.createChatMessageResponsePdu(receivedPdu.getUserName(), 0, 0, 0, 0,
						client.getNumberOfReceivedChatMessages(), receivedPdu.getClientThreadName(),
						(System.nanoTime() - client.getStartTime()));

				if (responsePdu.getServerTime() / 1000000 > 100) {
					log.debug(Thread.currentThread().getName()
							+ ", Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: "
							+ responsePdu.getServerTime() + " ns = " + responsePdu.getServerTime() / 1000000 + " ms");
				}

				try {
					sendPduToClient(client, responsePdu);
					log.debug("Chat-Message-Response-PDU an " + receivedPdu.getUserName() + " gesendet");
				} catch (Exception e) {
					log.debug("Senden einer Chat-Message-Response-PDU an " + client.getUserName() + " nicht moeglich");

				}
			}
			log.debug("Aktuelle Laenge der Clientliste: " + clients.size());
		}

	}

	public void sendLoginListUpdateEvent(ChatPDU pdu) {
		// Liste der eingeloggten bzw. sich einloggenden User ermitteln
		Vector<String> clientList = clients.getRegisteredClientNameList();

		log.debug("Aktuelle Clientliste, die an die Clients uebertragen wird: " + clientList);

		pdu.setClients(clientList);

		Vector<String> clientList2 = clients.getClientNameList();
		for (String s : new Vector<String>(clientList2)) {
			log.debug("Fuer " + s + " wird Login- oder Logout-Event-PDU an alle aktiven Clients gesendet");

			ClientListEntry client = clients.getClient(s);
			try {
				if (client != null) {

					sendPduToClient(client, pdu);
					log.debug("Login- oder Logout-Event-PDU an " + client.getUserName() + " gesendet");
					clients.incrNumberOfSentChatEvents(client.getUserName());
				}
			} catch (Exception e) {
				log.error("Senden einer Login- oder Logout-Event-PDU an " + s + " nicht moeglich");

			}
		}

	}

	@Override
	public void sendPduToClient(ClientListEntry client, ChatPDU pdu) {

		try {
			client.getSession().getBasicRemote().sendObject(pdu);
		} catch (IOException | EncodeException e) {
			log.error(e.getMessage());
		}

	}

}
