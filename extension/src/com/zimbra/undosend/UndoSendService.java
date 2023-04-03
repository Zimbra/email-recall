package com.zimbra.undosend;

import com.google.common.io.Closeables;
import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraCookie;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.client.LmcSession;
import com.zimbra.cs.cmbsearch.CrossMailboxSearch;
import com.zimbra.cs.httpclient.URLUtil;
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ParseMailboxID;
import com.zimbra.cs.util.SoapCLI;
import com.zimbra.soap.JaxbUtil;
import com.zimbra.soap.admin.type.MessageInfo;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

/**
 * @author manjeetkumar
 */
public class UndosendService extends SoapCLI {

    private int permailboxLimit = 1000;
    private int limit = -1;
    private File outputDir;
    private String mAuthToken = null;
    private boolean isVerbose = false;
    private boolean inDumpster = false;

    protected UndosendService() throws ServiceException {
    }

    public void getResult(String messageID) {
        try {
            SearchParams params = new SearchParams();
            List<String> mailboxes = new ArrayList<>();
            mailboxes.add("*");
            params.setQueryString("is:anywhere after:-120minutes msgid:" + messageID);

            LmcSession lmcSession = auth();
            mAuthToken = lmcSession.getAuthToken().getValue();

            int timeout = 0;
            CrossMailboxSearch xmbs = new CrossMailboxSearch();
            xmbs.setTimeout(timeout > 0 ? timeout * 1000 : timeout);

            for (String id : mailboxes) {
                xmbs.add(ParseMailboxID.parse(id));
            }

            AuthToken auth = AuthToken.getAuthToken(mAuthToken);
            for (CrossMailboxSearch.Task task : xmbs.search()) {
                File result = task.run(auth, params, permailboxLimit);
                Map<String, String> resultMap = UndosendUtility.getMappedDataFromXML(result);
                print(task.server, result);
                result.delete();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getCommandUsage() {
        return null;
    }

    private void print(String server, File file) throws ServiceException {
        Unmarshaller unmarshaller = JaxbUtil.createUnmarshaller(MessageInfo.class);
        int count = 0;
        InputStream input = null;
        XMLEventReader reader = null;
        try {
            input = new FileInputStream(file);
            reader = XMLInputFactory.newInstance().createXMLEventReader(input);
            while (true) {
                if (limit > 0 && count >= limit) {
                    break;
                }
                XMLEvent event = reader.peek();
                if (event == null) {
                    break;
                }
                if (event instanceof StartElement) {
                    StartElement start = (StartElement) event;
                    if (MailConstants.E_MSG.equals(start.getName().getLocalPart())) {
                        JAXBElement<MessageInfo> el = unmarshaller.unmarshal(reader, MessageInfo.class);
                        MessageInfo msg = el.getValue();
                        System.out.println(++count + ")");
                        //print(msg);
                        if (outputDir != null) {
                            saveMessageBody(count, server, new ItemId(msg.getId(), (String) null));
                        }
                        System.out.println();
                        continue;
                    }
                }
                reader.next();
            }
            file.delete();
        } catch (IOException e) {
            throw ServiceException.FAILURE("Failed to parse XML file=" + file, e);
        } catch (JAXBException e) {
            throw ServiceException.FAILURE("Failed to parse XML file=" + file, e);
        } catch (XMLStreamException e) {
            throw ServiceException.FAILURE("Failed to parse XML file=" + file, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignore) {
                }
            }
            Closeables.closeQuietly(input);
        }
        if (count == 0) {
            System.out.println("No results found");
        }
    }

    private void saveMessageBody(int num, String server, ItemId itemId) throws ServiceException {
        String path = "/service/content/get";

        BasicCookieStore state = new BasicCookieStore();
        BasicClientCookie cookie = new BasicClientCookie(ZimbraCookie.COOKIE_ZM_AUTH_TOKEN, mAuthToken);
        cookie.setDomain(server);
        cookie.setPath("/");
        cookie.setSecure(false);
        state.addCookie(cookie);

        HttpClientConnectionManager connManager = new BasicHttpClientConnectionManager();
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(30000)
                .build();
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setDefaultCookieStore(state);

        CloseableHttpClient client = httpClientBuilder.setConnectionManager(connManager)
                .setDefaultRequestConfig(defaultRequestConfig)
                .build();

        // make the get
        Server svr = Provisioning.getInstance().get(Key.ServerBy.name, server);
        String url = URLUtil.getServiceURL(svr, path + "?id=" + itemId, false);
        if (inDumpster) {
            url += "&dumpster=1";
        }

        StringBuilder fileName = new StringBuilder();
        Formatter formatter = new Formatter();
        fileName.append(formatter.format("%05d", num));
        fileName.append('_');

        String accountPart = itemId.getAccountId();
        int idPart = itemId.getId();
        if (accountPart != null && accountPart.length() > 0) {
            fileName.append(accountPart).append("_");
        }
        fileName.append(idPart);

        if (isVerbose) {
            System.out.println("Fetching result " + num + " at URL \"" + url + "\" into file \"" + fileName + "\"");
        }

        // have to replace the /'s with something legal for filenames!
        String filename = fileName.toString().replace('/', '-');
        HttpGet get = new HttpGet(url);
        try (OutputStream out = new FileOutputStream(new File(outputDir, filename));) {
            CloseableHttpResponse response = client.execute(get);
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                InputStream in = response.getEntity().getContent();
                ByteUtil.copy(in, true, out, true);
            } else {
                System.err.println("HTTP Error (" + status + ") attempting to retrieve result " + num + " from " + url);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            get.releaseConnection();
            formatter.close();
        }
    }
}