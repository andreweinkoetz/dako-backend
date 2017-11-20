package edu.hm.dako.chat.server;

import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ChatPDUDecoder;
import edu.hm.dako.chat.common.ChatPDUEncoder;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ClientListEntry;
import edu.hm.dako.chat.common.Pdutype;

@ServerEndpoint(value = "/advancedchat", encoders = { ChatPDUEncoder.class }, decoders = { ChatPDUDecoder.class })
public class AdvancedWebSocketServer extends AbstractWebSocketServer {

	@Override
	@OnOpen
	public void open(Session session) {
		this.session = session;
		sess.add(session);
		System.out.println(session.getId());
		System.out.println(session.getOpenSessions());
	}

	@Override
	@OnError
	public void error(Throwable t) {
		sess.remove(session);

	}

	@Override
	@OnClose
	public void close() {
		System.out.println("Client meldet sich ab: " + clients.getClient(userName));
		if (removeClientOnClose()) {
			System.out.println("Closed Session for " + userName);
			sess.remove(session);
			ChatPDU pdu = new ChatPDU();
			pdu.setPduType(Pdutype.LOGOUT_EVENT);
			pdu.setUserName(userName);
			pdu.setEventUserName(userName);
			pdu.setClients(clients.getClientNameList());
			System.out.println(clients.getClientNameList());
			sendLoginListUpdateEvent(clients.getClientNameList(), pdu);
		}
	}
	
	private boolean removeClientOnClose() {
		if(clients.getClient(userName).getStatus() != ClientConversationStatus.UNREGISTERING) {
			clients.deleteClientWithoutCondition(userName);
			return true;
		}
		return false;
	}

	@Override
	@OnMessage
	public void handleIncomingPDU(ChatPDU receivedPdu) throws IOException {

		System.out.println(clients);

		if (checkIfClientIsDeletable() == true) {
			System.out.println("User kann entfernt werden, checkIfClientIsDeletable liefert true fuer " + userName);
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
				try {
					loginEventConfirmAction(receivedPdu);
				} catch (Exception e) {
					// ExceptionHandler.logException(e);
				}
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
				System.out.println("Falsche PDU empfangen von Client: " + receivedPdu.getUserName() + ", PduType: "
						+ receivedPdu.getPduType());
				break;
			}
		} catch (Exception e) {
			System.out.println("Exception bei der Nachrichtenverarbeitung");
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
					System.out.println(
							"Laenge der Clientliste vor dem Entfernen von " + userName + ": " + clients.size());
					if (clients.deleteClient(userName) == true) {
						// Jetzt kann auch Worker-Thread beendet werden

						System.out.println(
								"Laenge der Clientliste nach dem Entfernen von " + userName + ": " + clients.size());
						System.out.println("Worker-Thread fuer " + userName + " zum Beenden vorgemerkt");
						return true;
					}
				}
			}
		}

		// Garbage Collection in der Clientliste durchfuehren
		Vector<String> deletedClients = clients.gcClientList();
		if (deletedClients.contains(userName)) {
			System.out.println("Ueber Garbage Collector ermittelt: Laufender Worker-Thread fuer " + userName
					+ " kann beendet werden");
			return true;
		}
		return false;
	}

	@Override
	public void loginRequestAction(ChatPDU receivedPdu) {
		System.out.println("Login Request");
		ChatPDU pdu = null;
		System.out.println("Login-Request-PDU fuer " + receivedPdu.getUserName() + " empfangen");

		// Neuer Client moechte sich einloggen, Client in Client-Liste
		// eintragen
		if (!clients.existsClient(receivedPdu.getUserName())) {
			System.out.println("User nicht in Clientliste: " + receivedPdu.getUserName());
			ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), session);
			client.setLoginTime(System.nanoTime());
			clients.createClient(receivedPdu.getUserName(), client);
			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.REGISTERING);
			System.out.println("User " + receivedPdu.getUserName() + " nun in Clientliste");
			userName = receivedPdu.getUserName();
			System.out.println("Laenge der Clientliste: " + clients.size());

			// ADVANCED_CHAT: Event-Warteliste erzeugen
			Vector<String> waitList = clients.createWaitList(receivedPdu.getUserName());

			// Login-Event an alle Clients (auch an den gerade aktuell
			// anfragenden) senden
			pdu = ChatPDU.createLoginEventPdu(userName, waitList, receivedPdu);
			sendLoginListUpdateEvent(waitList, pdu);
		} else {
			// User bereits angemeldet, Fehlermeldung an Client senden,
			// Fehlercode an Client senden
			pdu = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

			try {
				session.getBasicRemote().sendObject(pdu);
				;
				System.out.println("Login-Response-PDU an " + receivedPdu.getUserName() + " mit Fehlercode "
						+ ChatPDU.LOGIN_ERROR + " gesendet");
			} catch (Exception e) {
				System.out
						.println("Senden einer Login-Response-PDU an " + receivedPdu.getUserName() + " nicht moeglich");
			}
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public void logoutRequestAction(ChatPDU receivedPdu) {
		ChatPDU pdu;
		// logoutCounter.getAndIncrement();
		// System.out.println("Logout-Request von " + receivedPdu.getUserName() + ",
		// LogoutCount = "
		// + logoutCounter.get());

		System.out.println("Logout-Request-PDU von " + receivedPdu.getUserName() + " empfangen");

		if (!clients.existsClient(userName)) {
			System.out.println("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {
			// ADVANCED_CHAT: Event-Warteliste erzeugen
			Vector<String> waitList = clients.createWaitList(receivedPdu.getUserName());

			// Client, der sich gerade ausloggt soll nicht in der Userliste im Client
			// erscheinen, er wird daher entfernt. Ein LogoutEvent muss er aber
			// erhalten
			Vector<String> waitListWithoutLoggingOutClient = new Vector<String>();
			waitListWithoutLoggingOutClient = (Vector<String>) waitList.clone();
			waitListWithoutLoggingOutClient.remove(receivedPdu.getUserName());
			System.out.println("Warteliste ohne sich ausloggenden Client: " + waitListWithoutLoggingOutClient);

			pdu = ChatPDU.createLogoutEventPdu(userName, waitListWithoutLoggingOutClient, receivedPdu);

			// Event an Client versenden
			System.out.println("Warteliste mit sich ausloggenden Client: " + waitList);
			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.UNREGISTERING);
			sendLoginListUpdateEvent(waitList, pdu);

			// ADVANCED_CHAT: Logout-Response darf hier noch nicht gesendet werden
			// (erst nach Empfang aller Confirms)
		}

	}

	@Override
	public void chatMessageRequestAction(ChatPDU receivedPdu) {

		System.out.println("Chat-Message-Request-PDU von " + receivedPdu.getUserName() + " mit Sequenznummer "
				+ receivedPdu.getSequenceNumber() + " empfangen");

		if (!clients.existsClient(receivedPdu.getUserName())) {
			System.out.println("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {

			// ADVANCED_CHAT: Event-Warteliste erzeugen
			Vector<String> waitList = clients.createWaitList(receivedPdu.getUserName());

			System.out.println("Warteliste: " + waitList);
			System.out.println("Anzahl der User in der Warteliste: " + waitList.size());

			ChatPDU pdu = ChatPDU.createChatMessageEventPdu(userName, receivedPdu);

			// Event an Clients senden
			for (String s : new Vector<String>(waitList)) {

				ClientListEntry client = clients.getClient(s);
				try {
					if ((client != null) && (client.getStatus() != ClientConversationStatus.UNREGISTERED)) {
						pdu.setUserName(client.getUserName());
						sendPduToClient(client, pdu);
						System.out.println("Chat-Event-PDU an " + client.getUserName() + " gesendet");
						// clients.incrNumberOfSentChatEvents(client.getUserName());
						// eventCounter.getAndIncrement();
						// System.out.println(userName + ": EventCounter erhoeht = " +
						// eventCounter.get()
						// + ", Aktueller ConfirmCounter = " + confirmCounter.get()
						// + ", Anzahl gesendeter ChatMessages von dem Client = "
						// + receivedPdu.getSequenceNumber());
					}
				} catch (Exception e) {
					System.out.println("Senden einer Chat-Event-PDU an " + client.getUserName() + " nicht moeglich");
				}
			}
		}

		// System.out.println("Aktuelle Laenge der Clientliste: " + clients.size());

		// Statistikdaten aktualisieren
		// clients.incrNumberOfReceivedChatMessages(receivedPdu.getUserName());
		// clients.setRequestStartTime(receivedPdu.getUserName(), startTime);

	}

	public void sendLoginListUpdateEvent(Vector<String> currentClientList, ChatPDU pdu) {
		Vector<String> clientList = currentClientList;

		for (String s : new Vector<String>(clientList)) {

			ClientListEntry client = clients.getClient(s);
			try {
				if (client != null) {

					sendPduToClient(client, pdu);
					// System.out.println("Login- oder Logout-Event-PDU an " + client.getUserName()
					// + ", ClientListe: " + pdu.getClients());
					// clients.incrNumberOfSentChatEvents(client.getUserName());
					// eventCounter.getAndIncrement();
					// System.out.println(userName + ": EventCounter bei Login/Logout erhoeht = "
					// + eventCounter.get() + ", ConfirmCounter = " + confirmCounter.get());
				}
			} catch (Exception e) {
				System.out.println("Senden einer Login- oder Logout-Event-PDU an " + s + " nicht moeglich");
			}
		}

	}

	@Override
	public void sendPduToClient(ClientListEntry client, ChatPDU pdu) {
		try {
			client.getSession().getBasicRemote().sendObject(pdu);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (EncodeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void chatMessageEventConfirmAction(ChatPDU receivedPdu) {
		System.out.println("Chat-Message-Event-Confirm-PDU von " + receivedPdu.getUserName()
				+ " fuer initierenden Client " + receivedPdu.getEventUserName() + " empfangen");

		String eventInitiatorClient;
		String confirmSenderClient;

		// Empfangene Confirms hochzaehlen
		// clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());
		// confirmCounter.getAndIncrement();
		// System.out.println(userName + ": ConfirmCounter fuer ChatMessage erhoeht = "
		// + confirmCounter.get()
		// + ", Aktueller EventCounter = " + eventCounter.get()
		// + ", Anzahl gesendeter ChatMessages von dem Client = " +
		// receivedPdu.getSequenceNumber());

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
						System.out.println(Thread.currentThread().getName()
								+ ", Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: "
								+ responsePdu.getServerTime() + " ns = " + responsePdu.getServerTime() / 1000000
								+ " ms");
					}

					try {
						sendPduToClient(client, responsePdu);
						System.out.println(
								"Chat-Message-Response-PDU an " + receivedPdu.getEventUserName() + " gesendet");
					} catch (Exception e) {
						System.out.println("Senden einer Chat-Message-Response-PDU an " + client.getUserName()
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

		System.out.println("Logout-Event-Confirm-PDU von " + receivedPdu.getUserName() + " fuer initierenden Client "
				+ receivedPdu.getEventUserName() + " empfangen");

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());
		// confirmCounter.getAndIncrement();
		// System.out.println(userName + ": ConfirmCounter fuer Logout erhoeht = " +
		// confirmCounter.get()
		// + ", Aktueller EventCounter = " + eventCounter.get());

		eventInitiatorClient = receivedPdu.getEventUserName();
		confirmSenderClient = receivedPdu.getUserName();
		System.out.println("Logout-EventConfirm: Event-Initiator: " + eventInitiatorClient + ", Confirm-Sender: "
				+ confirmSenderClient);

		System.out.println(confirmSenderClient + " aus der Warteliste von " + eventInitiatorClient + " austragen");

		try {
			if ((clients.deleteWaitListEntry(eventInitiatorClient, confirmSenderClient) == 0)) {
				// Wenn der letzte Logout-Confirm ankommt, muss auch ein
				// Logout-Response gesendet werden

				System.out.println(
						"Warteliste von " + eventInitiatorClient + " ist nun leer, alle Confirms fuer Logout erhalten");
				sendLogoutResponse(eventInitiatorClient);

				// System.out.println(
				// eventInitiatorClient + ": EventCounter beim Logout = " + eventCounter.get()
				// + ", ConfirmCounter beim Logout-Response = " + confirmCounter.get());

				// Worker-Thread des Clients, der den Logout-Request gesendet
				// hat, auch gleich zum Beenden markieren
				clients.finish(eventInitiatorClient);

				System.out.println("Laenge der Clientliste beim Vormerken zum Loeschen von " + eventInitiatorClient
						+ ": " + clients.size());
			}
		} catch (Exception e) {
			System.out.println("Logout-Event-Confirm-PDU fuer nicht vorhandenen Client erhalten: "
					+ receivedPdu.getEventUserName());
		}

	}

	private void loginEventConfirmAction(ChatPDU receivedPdu) {
		String eventInitiatorClient;
		String confirmSenderClient;

		System.out.println("Login-Event-Confirm-PDU von Client " + receivedPdu.getUserName() + " fuer initierenden "
				+ receivedPdu.getEventUserName() + " empfangen");

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());
		// confirmCounter.getAndIncrement();
		// System.out.println(userName + ": ConfirmCounter fuer Login erhoeht = " +
		// confirmCounter.get()
		// + ", Aktueller EventCounter = " + eventCounter.get());

		eventInitiatorClient = receivedPdu.getEventUserName();
		confirmSenderClient = receivedPdu.getUserName();
		System.out.println("Login-EventConfirm: Event-Initiator: " + eventInitiatorClient + ", Confirm-Sender: "
				+ confirmSenderClient);

		try {
			System.out.println(confirmSenderClient + " aus der Warteliste von " + eventInitiatorClient + " austragen");

			if ((clients.deleteWaitListEntry(eventInitiatorClient, confirmSenderClient) == 0)) {
				System.out.println(
						"Warteliste von " + eventInitiatorClient + " ist nun leer, alle Login-Event-Confirms erhalten");

				if (clients.getClient(eventInitiatorClient).getStatus() == ClientConversationStatus.REGISTERING) {

					// Der initiierende Client ist im Login-Vorgang

					ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(eventInitiatorClient, receivedPdu,
							clients.getClientNameList());

					// Login-Response senden

					try {
						sendPduToClient(clients.getClient(eventInitiatorClient), responsePdu);
					} catch (Exception e) {
						System.out.println(
								"Senden einer Login-Response-PDU an " + eventInitiatorClient + " fehlgeschlagen");
						System.out.println("Exception Message: " + e.getMessage());
						throw e;
					}

					System.out.println("Login-Response-PDU an Client " + eventInitiatorClient + " gesendet");
					clients.changeClientStatus(eventInitiatorClient, ClientConversationStatus.REGISTERED);
				}
			} else {
				System.out.println("Warteliste von " + eventInitiatorClient + " enthaelt noch "
						+ clients.getWaitListSize(eventInitiatorClient) + " Eintraege");
			}

		} catch (Exception e) {
			System.out
					.println("Login-Event-Confirm-PDU fuer nicht vorhandenen Client erhalten: " + eventInitiatorClient);
		}

	}

}
