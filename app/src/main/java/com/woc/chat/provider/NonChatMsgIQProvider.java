package com.woc.chat.provider;
import com.woc.chat.iq.NonChatMsgIQ;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
public class NonChatMsgIQProvider extends IQProvider<NonChatMsgIQ> {

	@Override
	public NonChatMsgIQ parse(XmlPullParser parser, int initialDepth)
			throws XmlPullParserException, IOException, SmackException {
		// TODO Auto-generated method stub
		NonChatMsgIQ msg=new NonChatMsgIQ("info");
		msg.setData(parser.getAttributeValue(1));
		msg.setMsgType(parser.getAttributeValue(0));
//		boolean isContinue=true;
//			while (isContinue) {
//				int n=parser.next();
//				
//				switch (n) {
//				case XmlPullParser.START_TAG:
//					String tag=parser.getName();
//					if("info".equals(tag))
//					{
//						System.out.println("info");
//						msg.setMsgType(parser.getAttributeValue(0));
//					}
//					if("data".equals(tag))
//					{
//						System.out.println("data");
//						String nextText=parser.nextText();
//						msg.setData(nextText);
//						System.out.println(nextText);
//					}
//					break;
//				case XmlPullParser.END_TAG:
//					System.out.println("END_TAG");
//					if("info".equals(parser.getName()))
//					{
//						isContinue=false;
//						System.out.println("end--------------->");
//					}
//					
//					break;
//			
//				}
//				
//			}
		return msg;
	}

}
