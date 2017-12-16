package edu.hm.dako.chat.server;

import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ChatPDUDecoder;
import edu.hm.dako.chat.common.ChatPDUEncoder;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ClientListEntry;
import edu.hm.dako.chat.common.Pdutype;

@ServerEndpoint(value = "/advancedchat", encoders = { ChatPDUEncoder.class }, decoders = { ChatPDUDecoder.class })
public class AdvancedWebSocketServer extends AbstractWebSocketServer {

	private static Log log = LogFactory.getLog(AdvancedWebSocketServer.class);

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
			sendLoginListUpdateEvent(clients.getClientNameList(), pdu);
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

		log.debug("PDU vom Typ: " + receivedPdu.getPduType() + " empfangen");

		if (checkIfClientIsDeletable() == true) {
			log.debug("User kann entfernt werden, checkIfClientIsDeletable liefert true fuer " + userName);
			finished = true;
			return;
		}

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

			case LOGIN_EVENT_CONFIRM:
				// Bestaetigung eines Login-Events angekommen
				loginEventConfirmAction(receivedPdu);
				break;

			case LOGOUT_EVENT_CONFIRM:
				// Bestaetigung eines Logout-Events angekommen
				logoutEventConfirmAction(receivedPdu);
				break;

			case CHAT_MESSAGE_EVENT_CONFIRM:
				// Bestaetigung eines Chat-Events angekommen
				chatMessageEventConfirmAction(receivedPdu);
				break;

			default:
				log.debug("Falsche PDU empfangen von Client: " + receivedPdu.getUserName() + ", PduType: "
						+ receivedPdu.getPduType());
				break;
			}
		} catch (Exception e) {
			log.error("Exception bei der Nachrichtenverarbeitung");
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
					log.debug("Laenge der Clientliste vor dem Entfernen von " + userName + ": " + clients.size());
					if (clients.deleteClient(userName) == true) {
						// Jetzt kann auch Worker-Thread beendet werden

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
			return true;
		}
		return false;
	}

	@Override
	public void loginRequestAction(ChatPDU receivedPdu) {

		ChatPDU pdu = null;
		log.debug("Login-Request-PDU fuer " + receivedPdu.getUserName() + " empfangen");

		// Neuer Client moechte sich einloggen, Client in Client-Liste
		// eintragen
		if (!clients.existsClient(receivedPdu.getUserName())) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
			// TODO Prüfen ob es hier nicht auch zu einer Race-Cond kommt. session könnte in
			// der Zwischenzeit überschrieben sein.
			ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), session);
			client.setLoginTime(System.nanoTime());
			clients.createClient(receivedPdu.getUserName(), client);
			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.REGISTERING);
			log.debug("User " + receivedPdu.getUserName() + " nun in Clientliste");
			userName = receivedPdu.getUserName();
			log.debug("Laenge der Clientliste: " + clients.size());

			// ADVANCED_CHAT: Event-Warteliste erzeugen
			Vector<String> waitList = clients.createWaitList(receivedPdu.getUserName(),
					clients.getRegisteredClientNameList());

			// Login-Event an alle Clients (auch an den gerade aktuell
			// anfragenden) senden
			pdu = ChatPDU.createLoginEventPdu(userName, waitList, receivedPdu);
			sendLoginListUpdateEvent(waitList, pdu);
		} else {
			// User bereits angemeldet, Fehlermeldung an Client senden,
			// Fehlercode an Client senden
			pdu = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

			try {
				synchronized (session) {
					session.getBasicRemote().sendObject(pdu);
				}

				log.debug("Login-Response-PDU an " + receivedPdu.getUserName() + " mit Fehlercode "
						+ ChatPDU.LOGIN_ERROR + " gesendet");
			} catch (Exception e) {
				log.error("Senden einer Login-Response-PDU an " + receivedPdu.getUserName() + " nicht moeglich");
				log.error(e.getMessage());
			}
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public void logoutRequestAction(ChatPDU receivedPdu) {
		ChatPDU pdu;

		log.debug("Logout-Request-PDU von " + receivedPdu.getUserName() + " empfangen");

		if (!clients.existsClient(userName)) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {
			// ADVANCED_CHAT: Event-Warteliste erzeugen
			Vector<String> waitList = clients.createWaitList(receivedPdu.getUserName(),
					clients.getRegisteredClientNameList());

			// Client, der sich gerade ausloggt soll nicht in der Userliste im Client
			// erscheinen, er wird daher entfernt. Ein LogoutEvent muss er aber
			// erhalten
			Vector<String> waitListWithoutLoggingOutClient = new Vector<String>();
			waitListWithoutLoggingOutClient = (Vector<String>) waitList.clone();
			waitListWithoutLoggingOutClient.remove(receivedPdu.getUserName());
			log.debug("Warteliste ohne sich ausloggenden Client: " + waitListWithoutLoggingOutClient);

			pdu = ChatPDU.createLogoutEventPdu(userName, waitListWithoutLoggingOutClient, receivedPdu);

			// Event an Client versenden
			log.debug("Warteliste mit sich ausloggenden Client: " + waitList);
			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.UNREGISTERING);
			sendLoginListUpdateEvent(waitList, pdu);

			// ADVANCED_CHAT: Logout-Response darf hier noch nicht gesendet werden
			// (erst nach Empfang aller Confirms)
		}

	}

	private AtomicBoolean isAccessable = new AtomicBoolean(true);

	@Override
	public void chatMessageRequestAction(ChatPDU receivedPdu) {

		log.debug("Chat-Message-Request-PDU von " + receivedPdu.getUserName() + " mit Sequenznummer "
				+ receivedPdu.getSequenceNumber() + " empfangen");

		if (!clients.existsClient(receivedPdu.getUserName())) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {

			clients.getClient(receivedPdu.getUserName())
					.setNumberOfReceivedChatMessages(receivedPdu.getSequenceNumber());

			// ADVANCED_CHAT: Event-Warteliste erzeugen
			Vector<String> waitList = clients.createWaitList(receivedPdu.getUserName(),
					clients.getRegisteredClientNameList());

			// log.debug("Warteliste: " + waitList);
			log.debug("Anzahl der User in der Warteliste von " + receivedPdu.getUserName() + ": " + waitList.size());

			ChatPDU pdu = ChatPDU.createChatMessageEventPdu(userName, receivedPdu);

			// Event an Clients senden
			if (isAccessable.getAndSet(false)) {

				for (String s : new Vector<String>(waitList)) {
					ClientListEntry client = clients.getClient(s);
					try {
						if ((client != null) && (client.getStatus() != ClientConversationStatus.UNREGISTERED)) {
							pdu.setUserName(client.getUserName());
							sendPduToClient(client, pdu);
							log.debug("Chat-Event-PDU an " + client.getUserName() + " gesendet");
							// clients.incrNumberOfSentChatEvents(client.getUserName());
							// eventCounter.getAndIncrement();
							// log.debug(userName + ": EventCounter erhoeht = " +
							// eventCounter.get()
							// + ", Aktueller ConfirmCounter = " + confirmCounter.get()
							// + ", Anzahl gesendeter ChatMessages von dem Client = "
							// + receivedPdu.getSequenceNumber());
						}
					} catch (Exception e) {
						log.error("Senden einer Chat-Event-PDU an " + client.getUserName() + " nicht moeglich");
						log.error(e.getMessage());
					}
				}
				isAccessable.set(true);
			}
		}

		log.debug("Aktuelle Laenge der Clientliste: " + clients.size());

	}

	public void sendLoginListUpdateEvent(Vector<String> currentClientList, ChatPDU pdu) {
		Vector<String> clientList = currentClientList;

		for (String s : new Vector<String>(clientList)) {

			ClientListEntry client = clients.getClient(s);
			try {
				if (client != null) {
					log.debug("Login- oder Logout-Event-PDU an " + client.getUserName() + ", ClientListe: "
							+ pdu.getClients());
					sendPduToClient(client, pdu);
				}
			} catch (Exception e) {
				log.error("Senden einer Login- oder Logout-Event-PDU an " + s + " nicht moeglich");
				log.error(e.getMessage());
			}
		}

	}

	@Override
	public synchronized void sendPduToClient(ClientListEntry client, ChatPDU pdu) {
		try {
			synchronized (client) {
				Session session = client.getSession();
				synchronized (session) {
					session.getBasicRemote().sendObject(pdu);
				}
			}
		} catch (IOException | EncodeException e) {
			log.error(e.getMessage());
		}

	}

	private void chatMessageEventConfirmAction(ChatPDU receivedPdu) {
		log.debug("Chat-Message-Event-Confirm-PDU von " + receivedPdu.getUserName() + " fuer initierenden Client "
				+ receivedPdu.getEventUserName() + " empfangen");

		String eventInitiatorClient;
		String confirmSenderClient;

		// Chat-Response-PDU fuer den initiierenden Client aufbauen und
		// senden, sofern alle Events-Confirms
		// der anderen aktiven Clients eingesammelt wurden

		try {
			eventInitiatorClient = receivedPdu.getEventUserName();
			confirmSenderClient = receivedPdu.getUserName();
			if ((clients.deleteWaitListEntry(eventInitiatorClient, confirmSenderClient) == 0)) {

				// Der User, der die Chat-Nachricht gesendet hatte, muss ermittelt
				// werden, da an ihn eine Response-PDU gesendet werden muss
				ClientListEntry client = clients.getClient(receivedPdu.getEventUserName());

				if (client != null) {
					ChatPDU responsePdu = ChatPDU.createChatMessageResponsePdu(receivedPdu.getEventUserName(), 0, 0, 0,
							0, client.getNumberOfReceivedChatMessages(), receivedPdu.getClientThreadName(),
							(System.nanoTime() - client.getStartTime()));

					if (responsePdu.getServerTime() / 1000000 > 100) {
						log.debug(Thread.currentThread().getName()
								+ ", Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: "
								+ responsePdu.getServerTime() + " ns = " + responsePdu.getServerTime() / 1000000
								+ " ms");
					}

					try {
						sendPduToClient(client, responsePdu);
						log.debug("Chat-Message-Response-PDU an " + receivedPdu.getEventUserName() + " gesendet");
					} catch (Exception e) {
						log.error("Senden einer Chat-Message-Response-PDU an " + client.getUserName()
								+ " nicht moeglich");

					}
				}
			}
		} catch (Exception e) {

		}

	}

	private void logoutEventConfirmAction(ChatPDU receivedPdu) {
		String eventInitiatorClient;
		String confirmSenderClient;

		log.debug("Logout-Event-Confirm-PDU von " + receivedPdu.getUserName() + " fuer initierenden Client "
				+ receivedPdu.getEventUserName() + " empfangen");

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());

		eventInitiatorClient = receivedPdu.getEventUserName();
		confirmSenderClient = receivedPdu.getUserName();
		log.debug("Logout-EventConfirm: Event-Initiator: " + eventInitiatorClient + ", Confirm-Sender: "
				+ confirmSenderClient);

		log.debug(confirmSenderClient + " aus der Warteliste von " + eventInitiatorClient + " austragen");

		try {
			if ((clients.deleteWaitListEntry(eventInitiatorClient, confirmSenderClient) == 0)) {
				// Wenn der letzte Logout-Confirm ankommt, muss auch ein
				// Logout-Response gesendet werden

				log.debug(
						"Warteliste von " + eventInitiatorClient + " ist nun leer, alle Confirms fuer Logout erhalten");
				sendLogoutResponse(eventInitiatorClient);

				// Worker-Thread des Clients, der den Logout-Request gesendet
				// hat, auch gleich zum Beenden markieren
				clients.finish(eventInitiatorClient);

				log.debug("Laenge der Clientliste beim Vormerken zum Loeschen von " + eventInitiatorClient + ": "
						+ clients.size());
			}
		} catch (Exception e) {
			log.error("Logout-Event-Confirm-PDU fuer nicht vorhandenen Client erhalten: "
					+ receivedPdu.getEventUserName());
		}

	}

	private void loginEventConfirmAction(ChatPDU receivedPdu) {
		String eventInitiatorClient;
		String confirmSenderClient;

		log.debug("Login-Event-Confirm-PDU von Client " + receivedPdu.getUserName() + " fuer initierenden "
				+ receivedPdu.getEventUserName() + " empfangen");

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());

		eventInitiatorClient = receivedPdu.getEventUserName();
		confirmSenderClient = receivedPdu.getUserName();
		log.debug("Login-EventConfirm: Event-Initiator: " + eventInitiatorClient + ", Confirm-Sender: "
				+ confirmSenderClient);

		try {
			log.debug(confirmSenderClient + " aus der Warteliste von " + eventInitiatorClient + " austragen");

			if ((clients.deleteWaitListEntry(eventInitiatorClient, confirmSenderClient) == 0)) {
				log.debug(
						"Warteliste von " + eventInitiatorClient + " ist nun leer, alle Login-Event-Confirms erhalten");

				if (clients.getClient(eventInitiatorClient).getStatus() == ClientConversationStatus.REGISTERING) {

					// Der initiierende Client ist im Login-Vorgang

					ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(eventInitiatorClient, receivedPdu,
							clients.getClientNameList());

					// Login-Response senden

					try {
						sendPduToClient(clients.getClient(eventInitiatorClient), responsePdu);
					} catch (Exception e) {
						log.error("Senden einer Login-Response-PDU an " + eventInitiatorClient + " fehlgeschlagen");
						log.error("Exception Message: " + e.getMessage());
						throw e;
					}

					log.debug("Login-Response-PDU an Client " + eventInitiatorClient + " gesendet");
					clients.changeClientStatus(eventInitiatorClient, ClientConversationStatus.REGISTERED);
				}
			} else {
				log.debug("Warteliste von " + eventInitiatorClient + " enthaelt noch "
						+ clients.getWaitListSize(eventInitiatorClient) + " Eintraege");
			}

		} catch (Exception e) {
			log.error("Login-Event-Confirm-PDU fuer nicht vorhandenen Client erhalten: " + eventInitiatorClient);
		}

	}

}
