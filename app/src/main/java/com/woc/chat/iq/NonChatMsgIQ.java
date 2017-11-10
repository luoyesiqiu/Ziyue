package com.woc.chat.iq;

import org.jivesoftware.smack.packet.IQ;

public class NonChatMsgIQ extends IQ{
	private String msgType;
	private final String NAMESPACE="match:iq:info";
	private String data;
	private String childElementName;
	
	
	
	public NonChatMsgIQ(String childElementName) {
		super(childElementName);
		// TODO Auto-generated constructor stub
		this.childElementName=childElementName;
	}

	
	public NonChatMsgIQ(IQ stanza) {
		// TODO Auto-generated constructor stub
		super(stanza);
		
	}
	
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
		// TODO Auto-generated method stub
		xml.xmlnsAttribute(NAMESPACE);
		xml.attribute("type", msgType);
		xml.attribute("data", data);
		xml.rightAngleBracket();

		
		
		return xml;
	}

	public String getNamespace()
	{
		return NAMESPACE;
	}

	public String getElementName()
	{
		return childElementName;
	}
	
	public String getMsgType() {
		
		return msgType;
	}


	public void setMsgType(String type) {
		this.msgType = type;
	}


	public String getData() {
		return data;
	}


	public void setData(String data) {
		this.data = data;
	}

}
