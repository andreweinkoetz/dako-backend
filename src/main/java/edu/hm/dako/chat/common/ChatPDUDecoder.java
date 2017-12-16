package edu.hm.dako.chat.common;

import java.io.IOException;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ChatPDUDecoder implements Decoder.Text<ChatPDU> {

	private static Log log = LogFactory.getLog(ChatPDUDecoder.class);
	
	@Override
	public void destroy() {
	}

	@Override
	public void init(EndpointConfig arg0) {
	}

	@Override
	public ChatPDU decode(String arg0) throws DecodeException {
		ChatPDU pdu = null;
		ObjectMapper objectMapper = new ObjectMapper();

		if (willDecode(arg0)) {

			try {
				pdu = objectMapper.readValue(arg0, ChatPDU.class);
			} catch (IOException e) {
				log.error(e.getMessage());
			}

		}
		return pdu;
	}

	@Override
	public boolean willDecode(String arg0) {
		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.canDeserialize(objectMapper.constructType(ChatPDU.class));
	}

}
