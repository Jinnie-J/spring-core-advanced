package jinnie.proxy.pureproxy.proxy;

import jinnie.proxy.pureproxy.proxy.code.CacheProxy;
import jinnie.proxy.pureproxy.proxy.code.ProxyPatternClient;
import jinnie.proxy.pureproxy.proxy.code.RealSubject;
import org.junit.jupiter.api.Test;


public class ProxyPatternTest {

    @Test
    void noProxyTest(){
        RealSubject realSubject = new RealSubject();
        ProxyPatternClient client = new ProxyPatternClient(realSubject);
        client.execute();
        client.execute();
        client.execute();
    }

    @Test
    void cacheProxyTest(){
        RealSubject realSubject = new RealSubject();
        CacheProxy cacheProxy = new CacheProxy(realSubject);
        ProxyPatternClient client = new ProxyPatternClient(cacheProxy);
        client.execute();
        client.execute();
        client.execute();
    }
}


