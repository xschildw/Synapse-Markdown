package org.sagebionetworks.markdown.parsers;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.sagebionetworks.markdown.constants.WidgetConstants;
import org.sagebionetworks.markdown.constants.MarkdownRegExConstants;
import org.sagebionetworks.markdown.utils.ServerMarkdownUtils;
import org.sagebionetworks.markdown.utils.SharedMarkdownUtils;

public class LinkParser extends BasicMarkdownElementParser  {
	Pattern p1= Pattern.compile(MarkdownRegExConstants.LINK_REGEX, Pattern.DOTALL);
	Pattern protocol = Pattern.compile(MarkdownRegExConstants.LINK_URL_PROTOCOL, Pattern.DOTALL);
	MarkdownExtractor extractor;
	MarkdownElementParser widgetParser;
	private List<MarkdownElementParser> simpleParsers;
	
	@Override
	public void reset(List<MarkdownElementParser> simpleParsers) {
		extractor = new MarkdownExtractor();
		for(int i = 0; i < simpleParsers.size(); i++) {
			MarkdownElementParser parser = simpleParsers.get(i);
			if(parser instanceof SynapseMarkdownWidgetParser) {
				widgetParser = parser;
				 break;
			} 
		}
		this.simpleParsers = simpleParsers;
	}
	
	private String getCurrentDivID() {
		return WidgetConstants.DIV_ID_LINK_PREFIX + extractor.getCurrentContainerId() + SharedMarkdownUtils.getPreviewSuffix(isPreview);
	}

	@Override
	public void processLine(MarkdownElements line) {
		String input = line.getMarkdown();
		String bookmarkTarget = WidgetConstants.BOOKMARK_LINK_IDENTIFIER + ":";
		Matcher m = p1.matcher(input);
		StringBuffer sb = new StringBuffer();
		while(m.find()) {
			String text = input.substring(m.start(2), m.end(2));
			String url = input.substring(m.start(3), m.end(3));
			StringBuilder updated = new StringBuilder();
			
			//If the "url" targets a bookmarked element in the page, replace it with widget syntax 
			//for the renderer to attach a handler
			if(url.contains(bookmarkTarget)) {
				updated.append(WidgetConstants.WIDGET_START_MARKDOWN + WidgetConstants.BOOKMARK_CONTENT_TYPE + "?");
				updated.append(WidgetConstants.TEXT_KEY + "=" + text + "&");
				updated.append(WidgetConstants.INLINE_WIDGET_KEY + "=true&");
				updated.append(WidgetConstants.BOOKMARK_KEY + "=" +	url.substring(bookmarkTarget.length()));
				updated.append(WidgetConstants.WIDGET_END_MARKDOWN);			
			} else {
				//Check for incomplete urls (i.e. urls starting without http/ftp/file/#)
				String testUrl = url.toLowerCase();
				Matcher protocolMatcher = protocol.matcher(testUrl);
				if(!protocolMatcher.find()) {
					if (!testUrl.startsWith("#"))
						url = WidgetConstants.URL_PROTOCOL + url; 
					else {
						//starts with '#'.  if does not include bang, add it here
						if(testUrl.length() > 1 && testUrl.charAt(1) != '!')
							url = "#!" + url.substring(1);
					}
				}
				
				StringBuilder html = new StringBuilder();
				html.append(ServerMarkdownUtils.getStartLink(getClientHostString(), url));
				html.append(url + "\">");
				String processedText = runSimpleParsers(text, simpleParsers);
				html.append(processedText + ServerMarkdownUtils.END_LINK);
				extractor.putContainerIdToContent(getCurrentDivID(), html.toString());
				
				updated.append(extractor.getContainerElementStart() + getCurrentDivID());
				updated.append("\">" + extractor.getContainerElementEnd());
			}
			//Escape the replacement string for bookmarks' widget syntax
			m.appendReplacement(sb, Matcher.quoteReplacement(updated.toString()));
		}
		m.appendTail(sb);
		line.updateMarkdown(sb.toString());
		//Check for new bookmark widget
		widgetParser.processLine(line);
	}
	
	
	@Override
	public void completeParse(Document doc) {
		ServerMarkdownUtils.insertExtractedContentToMarkdown(extractor, doc, true);
		//and extract any widgets that may have been in the link text
		widgetParser.completeParse(doc);
	}
}
