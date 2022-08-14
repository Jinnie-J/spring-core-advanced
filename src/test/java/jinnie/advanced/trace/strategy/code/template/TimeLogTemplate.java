package jinnie.advanced.trace.strategy.code.template;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeLogTemplate {

    public void execute(Callback callback){
        long startTiem = System.currentTimeMillis();
        //비즈니스 로직 실행
        callback.call();  //위임
        //비즈니스 로직 종료
        long endTime = System.currentTimeMillis();
        long resultTime = endTime - startTiem;
        log.info("resultTime={}", resultTime);
    }
}
