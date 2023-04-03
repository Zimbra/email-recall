/*

Copyright (C) 2016-2020  Barry de Graaff

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/.

*/

package com.zimbra.undosend;

import com.zimbra.common.mime.shim.JavaMailMimeMessage;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.extension.ExtensionHttpHandler;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.servlet.util.AuthUtil;
import com.zimbra.cs.util.JMSession;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UndosendHandler extends ExtensionHttpHandler {

    /**
     * The path under which the handler is registered for an extension.
     * return "/undosend" makes it show up under:
     * https://testserver.example.com/service/extension/undosend
     *
     * @return path
     */
    @Override
    public String getPath() {
        return "/undosend";
    }

    /**
     * Processes HTTP GET requests.
     *
     * @param req  request message
     * @param resp response message
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.getOutputStream().print("com.zimbra.undosend is installed.");
    }

    /**
     * Processes HTTP POST requests.
     *
     * @param req  request message
     * @param resp response message
     */
    /* https://stackoverflow.com/questions/2422468/how-to-upload-files-to-server-using-jsp-servlet
    nano /opt/zimbra/jetty_base/etc/service.web.xml.in
    nano /opt/zimbra/jetty_base/webapps/service/WEB-INF/web.xml
    Add multipart config to enable HttpServletRequest.getPart() and HttpServletRequest.getParts()
        <servlet>
          <servlet-name>ExtensionDispatcherServlet</servlet-name>
          <servlet-class>com.zimbra.cs.extension.ExtensionDispatcherServlet</servlet-class>
          <async-supported>true</async-supported>
          <load-on-startup>2</load-on-startup>
          <init-param>
            <param-name>allowed.ports</param-name>
            <param-value>8080, 8443, 7071, 7070, 7072, 7443</param-value>
          </init-param>
        <multipart-config>
        </multipart-config>
        </servlet>
    And restart Zimbra
    */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        //all authentication is done by AuthUtil.getAuthTokenFromHttpReq, returns null if unauthorized
        final AuthToken authToken = AuthUtil.getAuthTokenFromHttpReq(req, resp, false, true);
        if (authToken != null) {
            try {
                Account account = Provisioning.getInstance().getAccountById(authToken.getAccountId());
                JSONObject mailObjectJSON = new JSONObject(IOUtils.toString(req.getPart("jsondata").getInputStream(), StandardCharsets.UTF_8));
                if (fetchMail(req, authToken, mailObjectJSON, account)) {
                    resp.getOutputStream().print("ok");
                    resp.setStatus(200);
                } else {
                    resp.getOutputStream().print("failed");
                    resp.setStatus(500);
                }
            } catch (Exception e) {
                System.out.println("com.zimbra.undosend probable JSON parse error");
                e.printStackTrace();
            }
        }
    }

    private static boolean checkPermission(ParsedMessage pm, Account account) {
        try {
            String[] aliases = account.getAliases();
            String mailAddr = account.getMail();
            String from = pm.getSenderEmail();

            if (mailAddr.equals(from)) {
                return true;
            }

            for (String alias : aliases) {
                if (alias.equals(from)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            System.out.println("com.zimbra.undosend unexpected exception, could not match email FROM header against account mail address/alias");
            ex.printStackTrace();
            return false;
        }
    }


    public boolean fetchMail(HttpServletRequest req, AuthToken authToken, JSONObject mailObject, Account account) {
        try {
            try {
                URL url;
                String uri;
                HttpURLConnection connection;

                uri = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + "/service/home/~/?auth=co&id=" + mailObject.getString("id");
                url = new URL(uri);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("charset", "utf-8");
                connection.setRequestProperty("Content-Length", "0");
                connection.setRequestProperty("Cookie", "ZM_AUTH_TOKEN=" + authToken.getEncoded() + ";");
                connection.setUseCaches(false);

                if (connection.getResponseCode() == 200) {
                    MimeMessage message = new JavaMailMimeMessage(JMSession.getSession(), connection.getInputStream());
                    ParsedMessage pm = new ParsedMessage(message, false);
                    if (checkPermission(pm, account)) {
                        System.out.println("Undo sending of message:" + pm.getMessageID());
                        String messageID = pm.getMessageID().replace("<", "").replace(">", "");
                        String regex = "^[a-zA-Z0-9.@-]+$"; //Example Zimbra generated id: 1889798574.2.1597394282373.JavaMail.zimbra@barrydegraaff.tk
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(messageID);

                        if ((matcher.matches()) && (messageID.length() > 40)) {
                            UndosendService utility = new UndosendService();
                            utility.getResult(messageID);
                        } else {
                            System.out.println("com.zimbra.undosend Message-Id format invalid, someone hacking here?");
                            throw new Exception("com.zimbra.undosend Message-Id format invalid, someone hacking here?");
                        }
                    } else {
                        System.out.println("com.zimbra.undosend permission denied, email FROM header does not match account mail address/alias");
                        throw new Exception("com.zimbra.undosend permission denied, email FROM header does not match account mail address/alias");
                    }

                } else {
                    System.out.println("com.zimbra.undosend cannot delete email");
                    throw new Exception("com.zimbra.undosend cannot delete email");
                }
                return true;
            } catch (Exception e) {
                ZimbraLog.extensions.info(e);
                return false;
            }
        } catch (Exception e) {
            System.out.println("com.zimbra.undosend cannot fetch email");
            ZimbraLog.extensions.info(e);
            return false;
        }
    }


}
