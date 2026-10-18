package javax.microedition.io;

import java.io.IOException;

public interface HttpConnection extends ContentConnection {
    String HEAD = "HEAD";
    String GET = "GET";
    String POST = "POST";
    int HTTP_OK = 200;
    int HTTP_CREATED = 201;
    int HTTP_ACCEPTED = 202;
    int HTTP_NO_CONTENT = 204;
    int HTTP_MOVED_PERM = 301;
    int HTTP_MOVED_TEMP = 302;
    int HTTP_SEE_OTHER = 303;
    int HTTP_NOT_MODIFIED = 304;
    int HTTP_BAD_REQUEST = 400;
    int HTTP_UNAUTHORIZED = 401;
    int HTTP_FORBIDDEN = 403;
    int HTTP_NOT_FOUND = 404;
    int HTTP_INTERNAL_ERROR = 500;
    int HTTP_UNAVAILABLE = 503;

    String getURL();

    String getProtocol();

    String getHost();

    String getFile();

    String getRef();

    String getQuery();

    int getPort();

    String getRequestMethod();

    void setRequestMethod(String method) throws IOException;

    String getRequestProperty(String key);

    void setRequestProperty(String key, String value) throws IOException;

    int getResponseCode() throws IOException;

    String getResponseMessage() throws IOException;

    long getExpiration() throws IOException;

    long getDate() throws IOException;

    long getLastModified() throws IOException;

    String getHeaderField(String name) throws IOException;

    int getHeaderFieldInt(String name, int def) throws IOException;

    long getHeaderFieldDate(String name, long def) throws IOException;

    String getHeaderField(int n) throws IOException;

    String getHeaderFieldKey(int n) throws IOException;
}
