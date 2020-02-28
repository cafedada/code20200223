package io.vilya.code20200223;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

/**
 * 
 * @author cafedada
 *
 */
public class Application {

	private static final Logger logger = LoggerFactory.getLogger(Application.class);

	private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings();

	private static final VisitorWriter DEFAULT_WRITER = new DefaultVisitorWriter();
	
	private static final Map<String, VisitorWriter> PRIMARY_SUPPORTED_ACCEPT = Map.of("application/json",
			new JsonVisitorWriter(), "application/xml", new XmlVisitorWriter(), "text/html", DEFAULT_WRITER);

	public static void main(String[] args) {
		Vertx vertx = Vertx.factory.vertx();

		HttpServer httpServer = vertx.createHttpServer();
		httpServer.requestHandler(request -> handle(request, request.response()));

		httpServer.listen(8080, "127.0.0.1").onSuccess(r -> {
			logger.info("started");
		});
	}

	private static void handle(HttpServerRequest request, HttpServerResponse response) {
		request.headers().forEach(entry -> logger.info("{}: {}", entry.getKey(), entry.getValue()));
		
		String accept = request.getHeader(HttpHeaders.ACCEPT);
		String contentType = COMMA_SPLITTER.splitToStream(accept)
				.filter(Application::primarySupported)
				.findFirst()
				.orElse("text/html");
		
		VisitorWriter writer = Optional.<VisitorWriter>ofNullable(getVisitorWriter(contentType)).orElse(DEFAULT_WRITER);

		Visitor visitor = new Visitor(getVisitorIp(request));
		
		String body = writer.createBody(visitor);
		
		response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()));
		response.putHeader(HttpHeaders.CONTENT_TYPE, contentType + "; charset=utf-8");
		response.putHeader(HttpHeaders.X_POWERED_BY, "U");
		
		response.end(body);
		response.close();
	}
	
	private static String getVisitorIp(HttpServerRequest request) {
		String xff = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
		if (!Strings.isNullOrEmpty(xff)) {
			return getVisitorIpFromXFF(xff); 
		}
		return request.remoteAddress().host();
	}
	
	private static String getVisitorIpFromXFF(String xff) {
		return Iterables.getFirst(COMMA_SPLITTER.split(xff), xff);
	}

	private static boolean primarySupported(String mimeType) {
		return PRIMARY_SUPPORTED_ACCEPT.containsKey(mimeType);
	}

	private static VisitorWriter getVisitorWriter(String mimeType) {
		return PRIMARY_SUPPORTED_ACCEPT.get(mimeType);
	}

	private static class Visitor {

		private String ip;

		public Visitor(String ip) {
			super();
			this.ip = ip;
		}

		public String getIp() {
			return ip;
		}

		public void setIp(String ip) {
			this.ip = ip;
		}

	}

	private interface VisitorWriter {

		String createBody(Visitor visitor);

	}

	private static class JsonVisitorWriter implements VisitorWriter {
		
		private static final ObjectMapper serializer = new ObjectMapper(new JsonFactory());

		@Override
		public String createBody(Visitor visitor) {
			try {
				return serializer.writeValueAsString(visitor);
			} catch (JsonProcessingException e) {
				throw new WriteFailedException(e);
			}
		}

	}

	private static class XmlVisitorWriter implements VisitorWriter {

		private static final ObjectMapper serializer = new XmlMapper();

		@Override
		public String createBody(Visitor visitor) {
			try {
				return serializer.writeValueAsString(visitor);
			} catch (JsonProcessingException e) {
				throw new WriteFailedException(e);
			}
		}

	}

	private static class DefaultVisitorWriter implements VisitorWriter {

		@Override
		public String createBody(Visitor visitor) {
			return visitor.getIp();
		}

	}

	private static class WriteFailedException extends RuntimeException {

		public WriteFailedException(Throwable cause) {
			super(cause);
		}

	}

}
