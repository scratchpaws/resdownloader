package downloader;

import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.protocol.HttpContext;

public class EasyCookieSpecProvider
    implements CookieSpecProvider {

    @Override
    public CookieSpec create(HttpContext httpContext) {
        return new EasyCookieSpec();
    }
}
