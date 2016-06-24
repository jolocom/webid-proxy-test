package com.danubetech.webidproxy.test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

public class WebIDProxyTest {

	private static final Log log = LogFactory.getLog(WebIDProxyTest.class);

	static String doTest = System.getProperty("webidproxy.test.alluserswillbedeleted");
	static String pathToSolid = System.getProperty("webidproxy.test.pathToSolid");
	static String pathToProxy = System.getProperty("webidproxy.test.pathToProxy");
	static String proxyUrl = System.getProperty("webidproxy.test.proxyUrl");
	static String webIdHost = System.getProperty("webidproxy.test.webIdHost");

	static HttpClient newHttpClient() throws Exception {

		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

		RFC6265CookieSpecProvider cookieSpecProvider = new RFC6265CookieSpecProvider();
		Lookup<CookieSpecProvider> cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider>create()
				.register(CookieSpecs.DEFAULT, cookieSpecProvider)
				.register(CookieSpecs.STANDARD, cookieSpecProvider)
				.register(CookieSpecs.STANDARD_STRICT, cookieSpecProvider)
				.build();

		RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
		requestConfigBuilder.setCookieSpec(CookieSpecs.DEFAULT);

		RequestConfig requestConfig = requestConfigBuilder.build();

		httpClientBuilder.setDefaultRequestConfig(requestConfig);
		httpClientBuilder.setDefaultCookieSpecRegistry(cookieSpecRegistry);
		httpClientBuilder.addInterceptorLast(MYHTTPREQUESTINTERCEPTOR);
		httpClientBuilder.addInterceptorFirst(MYHTTPRESPONSEINTERCEPTOR);

		return httpClientBuilder.build();
	}

	static void deleteSolidWebIdDirs() throws IOException {

		final String webIdHostWithoutPort = webIdHost.substring(0,  webIdHost.indexOf(':')); 

		File solidDir = new File(pathToSolid);
		File[] solidWebIdDirs = solidDir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {

				boolean ret = true;

				if (! pathname.isDirectory()) ret = false;
				if (! pathname.getAbsolutePath().endsWith(webIdHostWithoutPort)) ret = false;

				return ret;
			}
		});

		log.debug("Deleting " + solidWebIdDirs.length + " Solid WebID directories...");

		for (File solidWebIdDir : solidWebIdDirs) {

			log.debug("Deleting Solid WebID directory: " + solidWebIdDir.getAbsolutePath());
			FileUtils.deleteDirectory(solidWebIdDir);
		}
	}

	static void deleteProxyUsersDir() throws Exception {

		File proxyDir = new File(pathToProxy);
		File proxyUsersDir = new File(proxyDir, "users");

		log.debug("Deleting Proxy users directory: " + proxyUsersDir.getAbsolutePath());
		FileUtils.deleteDirectory(proxyUsersDir);
	}

	static void registerUser(String username, String password) throws Exception {

		String target = proxyUrl;
		if (! target.endsWith("/")) target += "/";
		target += "register";

		HttpClient httpClient = newHttpClient();
		HttpPost httpPost = new HttpPost(target);
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair> ();
		nameValuePairs.add(new BasicNameValuePair("username", username));
		nameValuePairs.add(new BasicNameValuePair("password", password));
		httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		HttpResponse httpResponse = httpClient.execute(httpPost);

		if (httpResponse.getStatusLine().getStatusCode() != 200) throw new Exception("Unexpected response to /register: " + httpResponse.getStatusLine().getStatusCode() + " " + httpResponse.getStatusLine().getReasonPhrase());

		final String webIdHostWithoutPort = webIdHost.substring(0,  webIdHost.indexOf(':')); 

		File[] checkFiles = new File[] {
				new File(new File(pathToSolid), username + "." + webIdHostWithoutPort),
				new File(new File(pathToProxy), "users/" + username),
				new File(new File(pathToProxy), "users/" + username + ".p12")
		};

		for (File checkFile : checkFiles) {

			log.debug("File " + checkFile.getAbsolutePath() + " has been created? " + checkFile.exists());
			if (! checkFile.exists()) throw new Exception("File " + checkFile.getAbsolutePath() + " has not been created.");
		}
	}

	static HttpClient loginUser(String username, String password) throws Exception {

		String target = proxyUrl;
		if (! target.endsWith("/")) target += "/";
		target += "login";

		HttpClient httpClient = newHttpClient();
		HttpPost httpPost = new HttpPost(target);
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair> ();
		nameValuePairs.add(new BasicNameValuePair("username", username));
		nameValuePairs.add(new BasicNameValuePair("password", password));
		httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		HttpResponse httpResponse = httpClient.execute(httpPost);

		if (httpResponse.getStatusLine().getStatusCode() != 200) throw new Exception("Unexpected response to /login: " + httpResponse.getStatusLine().getStatusCode() + " " + httpResponse.getStatusLine().getReasonPhrase());

		return httpClient;
	}

	static void getCard(HttpClient httpClient, String username, String card, int expectedStatusCode, String expectedContent) throws Exception {

		String cardUrl = "https://" + username + "." + webIdHost + "/profile/" + card;

		String target = proxyUrl;
		if (! target.endsWith("/")) target += "/";
		target += "proxy?url=";
		target += cardUrl;

		HttpGet httpGet = new HttpGet(target);
		HttpResponse httpResponse = httpClient.execute(httpGet);

		if (httpResponse.getStatusLine().getStatusCode() != expectedStatusCode) throw new Exception("Unexpected response to /proxy: " + httpResponse.getStatusLine().getStatusCode() + " " + httpResponse.getStatusLine().getReasonPhrase() + " (is not " + expectedStatusCode + ")");

		if (expectedContent != null) {

			HttpEntity httpEntity = httpResponse.getEntity();
			String httpEntityString = EntityUtils.toString(httpEntity);
			if (! httpEntityString.contains(expectedContent)) throw new Exception("Unexpected card content: " + httpEntityString);
		}
	}

	static void putCard(HttpClient httpClient, String username, String card) throws Exception {

		HttpEntity cardHttpEntity = new InputStreamEntity(WebIDProxyTest.class.getResourceAsStream("test.card"));

		String cardUrl = "https://" + username + "." + webIdHost + "/profile/" + card;

		String target = proxyUrl;
		if (! target.endsWith("/")) target += "/";
		target += "proxy?url=";
		target += cardUrl;

		HttpPut httpPut = new HttpPut(target);
		httpPut.setEntity(cardHttpEntity);
		HttpResponse httpResponse = httpClient.execute(httpPut);

		if (httpResponse.getStatusLine().getStatusCode() != 201) throw new Exception("Unexpected response to /proxy: " + httpResponse.getStatusLine().getStatusCode() + " " + httpResponse.getStatusLine().getReasonPhrase());
	}

	public static void run() throws Exception {

		if (doTest == null) {

			log.info("Skipping test. Use -Dwebidproxy.test.alluserswillbedeleted if you want to run the test.");
			return;
		}

		deleteSolidWebIdDirs();
		log.info("Successfully deleted Solid WebID directories.");

		deleteProxyUsersDir();
		log.info("Successfully deleted Proxy users directory.");

		registerUser("testuser1", "password");
		log.info("Successfully registered 'testuser1'");

		registerUser("testuser2", "password");
		log.info("Successfully registered 'testuser2'");

		HttpClient testuser1HttpClient = loginUser("testuser1", "password");
		log.info("Successfully logged in 'testuser1'");

		HttpClient testuser2HttpClient = loginUser("testuser2", "password");
		log.info("Successfully logged in 'testuser2'");

		getCard(testuser1HttpClient, "testuser1", "card", 200, "\"Created by solid-server\";");
		log.info("testuser1 card retrieved by testuser1");

		getCard(testuser2HttpClient, "testuser2", "card", 200, "\"Created by solid-server\";");
		log.info("testuser2 card retrieved by testuser2");

		putCard(testuser1HttpClient, "testuser1", "card2");
		log.info("testuser1 card2 sent by testuser1.");

		putCard(testuser2HttpClient, "testuser2", "card2");
		log.info("testuser2 card2 sent by testuser2.");

		getCard(testuser1HttpClient, "testuser1", "card2", 200, "\"WebID profile of Donald Duck\"");
		log.info("testuser1 card2 retrieved by testuser1");

		getCard(testuser2HttpClient, "testuser2", "card2", 200, "\"WebID profile of Donald Duck\"");
		log.info("testuser2 card2 retrieved by testuser2");

		getCard(testuser2HttpClient, "testuser1", "card2", 403, null);
		log.info("testuser1 card2 retrieved by testuser2");

		getCard(testuser1HttpClient, "testuser2", "card2", 403, null);
		log.info("testuser2 card2 retrieved by testuser1");
	}

	public static void main(String[] args) throws Exception {

		try {

			run();
		} catch (Exception ex) {

			log.warn(ex.getMessage(), ex);
			throw ex;
		}
	}

	private static HttpRequestInterceptor MYHTTPREQUESTINTERCEPTOR = new HttpRequestInterceptor() {

		@Override
		public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {

			log.debug("<< REQUEST: " + request.getRequestLine());

			for (Header header : request.getAllHeaders()) {

				log.debug("<< HEADER: " + header.getName() + " -> " + header.getValue());
			}
		}
	};

	private static HttpResponseInterceptor MYHTTPRESPONSEINTERCEPTOR = new HttpResponseInterceptor() {

		@Override
		public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {

			log.debug(">> RESPONSE: " + response.getStatusLine());

			for (Header header : response.getAllHeaders()) {

				log.debug(">> HEADER: " + header.getName() + " -> " + header.getValue());
			}
		}
	};
}
