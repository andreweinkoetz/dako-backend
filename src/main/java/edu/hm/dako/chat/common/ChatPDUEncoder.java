package edu.hm.dako.chat.common;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChatPDUEncoder implements Encoder.Text<ChatPDU> {

	private static Log log = LogFactory.getLog(ChatPDUEncoder.class);
	
	@Override
	public void destroy() {
	}

	@Override
	public void init(EndpointConfig arg0) {
	}

	@Override
	public synchronized String encode(ChatPDU arg0) throws EncodeException {
		ObjectMapper mapper = new ObjectMapper();
		String jsonInString = "";
		try {
			jsonInString = mapper.writeValueAsString(arg0);
		} catch (JsonProcessingException e) {
			log.error(e.getMessage());
		}

		return jsonInString;
	}

}
