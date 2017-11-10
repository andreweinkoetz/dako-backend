package edu.hm.dako.chat.client;

import java.net.URI;
import java.net.URISyntaxException;

public class MainClientApplication {
	
	public static void main(String[] args) {
        
		WebSocketClientEndpoint[] clientEndPoints = new WebSocketClientEndpoint[100];
		for(int i = 0; i<100;i++) {
			try {
	            // open websocket
	             clientEndPoints[i] = new WebSocketClientEndpoint(new URI("ws://localhost:8080/dako-backend-old/simplechat"));

	            // add listener
	             clientEndPoints[i] .addMessageHandler(new WebSocketClientEndpoint.MessageHandler() {
	                public void handleMessage(String message) {
	                    System.out.println(message);
	                }
	            });
	        } catch (URISyntaxException ex) {
	            System.err.println("URISyntaxException exception: " + ex.getMessage());
	        }
		}
		
		for (int i = 0; i < clientEndPoints.length; i++) {
            // send message to websocket
            clientEndPoints[i] .sendMessage("{'event':'addChannel','channel':'ok_btccny_ticker'}");
		}
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    }

}
