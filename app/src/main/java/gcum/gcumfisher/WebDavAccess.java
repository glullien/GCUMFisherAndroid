package gcum.gcumfisher;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.http.HttpVersion;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Gestion de la connection avec le cloud via WebDav
 */
class WebDavAccess {

    private final String host;
    private final String site;
    private final String root;
    private final String username;
    private final String password;

    private final HttpClient client;

    WebDavAccess(Context context) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException {
        this(context.getResources(), LoginActivity.getCredentials(context));
    }

    WebDavAccess(Resources resources, LoginActivity.Credentials credentials) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException {
        host = resources.getString(R.string.webdav_host);
        site = resources.getString(R.string.webdav_site);
        root = resources.getString(R.string.webdav_root);
        username = credentials.username;
        password = credentials.password;
        client = getHttpClient();
    }

    private void mkDirs(@NonNull String path, @NonNull List<String> dirs) throws IOException, DavException {
        mkDir(path, dirs.get(0));
        if (dirs.size() > 1) mkDirs(path + dirs.get(0), dirs.subList(1, dirs.size()));
    }

    void ensureExists(@NonNull String path, @NonNull List<String> dirs) throws IOException, DavException {
        if (!dir(path).contains(dirs.get(0))) mkDirs(path, dirs);
        else if (dirs.size() > 1) ensureExists(path + dirs.get(0), dirs.subList(1, dirs.size()));
    }

    void putFile(@NonNull String path, @NonNull String fileName, @NonNull String localFile) throws IOException, DavException {
        PutMethod method = new PutMethod(site + root + path + fileName);
        method.setRequestHeader("Content-type", "image/jpeg");
        method.setRequestEntity(new InputStreamRequestEntity(new FileInputStream(localFile)));
        int status = client.executeMethod(method);
        if (status != HttpStatus.SC_CREATED) throw new IOException("Http not ok: " + status);
    }

    @NonNull
    List<String> dir(@NonNull String path) throws IOException, DavException {
        String root = this.root + path;
        DavMethod method = new PropFindMethod(site + root, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_1);
        client.executeMethod(method);
        method.checkSuccess();
        if (!method.succeeded()) throw new IOException("Cannot dir(" + path + ")");
        MultiStatusResponse[] responses = method.getResponseBodyAsMultiStatus().getResponses();
        List<String> res = new ArrayList<>(responses.length);
        for (MultiStatusResponse response : responses) {
            String href = response.getHref();
            if (root.length() < href.length()) {
                String name = href.substring(root.length());
                if (name.charAt(0) != '.') res.add(name);
            }
        }
        return res;
    }

    private void mkDir(@NonNull String path, @NonNull String dir) throws IOException, DavException {
        DavMethod method = new MkColMethod(site + root + path + dir);
        client.executeMethod(method);
        method.checkSuccess();
        if (!method.succeeded()) throw new IOException("Cannot dir(" + path + ")");
    }

    /**
     * Supprime l'authentification du server.
     * Obligatoire car le certificat du cloud n'est pas certifié...
     */
    private class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);
            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[]{tm}, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

    /**
     * Création du client http utilisé par la librairie du webddav.
     * Cette grosse bidouille est obligatoire pour contourner les checks qui foirent parce que le certificat n'est certifié.
     */
    @NonNull
    private HttpClient getHttpClient() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
        HostConfiguration hostConfig = new HostConfiguration();
        hostConfig.setHost(host);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        final MySSLSocketFactory sf = new MySSLSocketFactory(trustStore);
        sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https", sf, 443));

        HttpConnectionManager connectionManager = new SimpleHttpConnectionManager() {
            @Override
            public HttpConnection getConnection(HostConfiguration hostConfiguration) {
                return super.getConnection(hostConfiguration);
            }

            @Override
            public HttpConnection getConnectionWithTimeout(HostConfiguration hostConfiguration, long timeout) {
                HttpConnection connection = super.getConnectionWithTimeout(hostConfiguration, timeout);
                Protocol protocol = connection.getProtocol();
                final ProtocolSocketFactory factory = protocol.getSocketFactory();
                ProtocolSocketFactory newFactory = new ProtocolSocketFactory() {
                    @Override
                    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException, UnknownHostException {
                        return factory.createSocket(host, port, localAddress, localPort);
                    }

                    @Override
                    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
                        Socket socket = sf.createSocket();
                        HttpParams compatParams = new BasicHttpParams();
                        HttpProtocolParams.setVersion(compatParams, HttpVersion.HTTP_1_1);
                        HttpProtocolParams.setContentCharset(compatParams, HTTP.ISO_8859_1);
                        sf.connectSocket(socket, host, port, localAddress, localPort, compatParams);
                        return socket;
                    }

                    @Override
                    public Socket createSocket(String host, int port) throws IOException {
                        return factory.createSocket(host, port);
                    }
                };
                Protocol newProtocol = new Protocol(protocol.getScheme(), newFactory, protocol.getDefaultPort());
                return new HttpConnection(connection.getHost(), connection.getPort(), newProtocol);
            }
        };
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        int maxHostConnections = 20;
        params.setMaxConnectionsPerHost(hostConfig, maxHostConnections);
        connectionManager.setParams(params);

        HttpClient client = new HttpClient(connectionManager);
        client.setHostConfiguration(hostConfig);
        Credentials creds = new UsernamePasswordCredentials(username, password);
        client.getState().setCredentials(AuthScope.ANY, creds);
        return client;
    }

}
