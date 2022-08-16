package jinnie.advanced.app.v5;

import jinnie.advanced.trace.callback.TraceCallback;
import jinnie.advanced.trace.callback.TraceTemplate;
import jinnie.advanced.trace.logtrace.LogTrace;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceV5 {

    private final OrderRepositoryV5 orderRepository;
    private final TraceTemplate template;

    public OrderServiceV5(OrderRepositoryV5 orderRepository, LogTrace trace){
        this.orderRepository = orderRepository;
        this.template = new TraceTemplate(trace);
    }

    public void orderItem(String itemId){

        template.execute("OrderService.orderItem()", (TraceCallback<Object>) () -> {
            orderRepository.save(itemId);
            return null;
        });
    }
}
