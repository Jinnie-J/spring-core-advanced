package jinnie.proxy;

import jinnie.advanced.trace.logtrace.LogTrace;
import jinnie.advanced.trace.logtrace.ThreadLocalLogTrace;
import jinnie.proxy.config.AppV1Config;
import jinnie.proxy.config.AppV2Config;
import jinnie.proxy.config.v1_proxy.ConcreteProxyConfig;
import jinnie.proxy.config.v1_proxy.InterfaceProxyConfig;
import jinnie.proxy.config.v2_dynamicproxy.DynamicProxyBasicConfig;
import jinnie.proxy.config.v2_dynamicproxy.DynamicProxyFilterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

//@Import({AppV1Config.class, AppV2Config.class})
//@Import(InterfaceProxyConfig.class)
//@Import(ConcreteProxyConfig.class)
//@Import(DynamicProxyBasicConfig.class)
@Import(DynamicProxyFilterConfig.class)
@SpringBootApplication(scanBasePackages = "jinnie.proxy.app") //주의
public class ProxyApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProxyApplication.class, args);
	}

	@Bean
	public LogTrace logTrace(){
		return new ThreadLocalLogTrace();
	}
}
