import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.lang.instrument.Instrumentation;

public class SSLAgent {
    public static void premain(String args, Instrumentation inst) throws Exception {
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{new AllTrustManager()}, new java.security.SecureRandom());
        SSLContext.setDefault(sc);
        HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
    }
}

class AllTrustManager implements X509TrustManager {
    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    public void checkClientTrusted(X509Certificate[] c, String a) { }
    public void checkServerTrusted(X509Certificate[] c, String a) { }
}
