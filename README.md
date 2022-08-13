# 스프링 핵심 원리 - 고급편

## 예제 만들기
 
### 예제 프로젝트 만들기 - V0
- 상품을 주문하는 프로세스로 가정하고, 일반적인 웹 애플리케이션에서 Controller -> Service -> Repository 이어지는 흐름을 최대한 단순하게 만들어보자.

    - @Repository: 컴포넌트 스캔의 대상이 된다. 따라서 스프링 빈으로 자동 등록된다.
    ```java
    if(itemId.equals("ex")){
        throw new IllegalStateException("예외 발생!");
    }
    sleep(1000);   
    ```
    - sleep(1000): 리포지토리는 상품을 젖아하는데 약 1초 정도 걸리는 것으로 가정하기 위해 1초 지연을 주었다. (1000ms)
    - 예외가 발생하는 상황도 확인하기 위해 파라미터 itemId의 값이 ex로 넘어오면 IllegalStateException 예외가 발생하도록 했다.
    - @Service: 컴포넌트 스캔의 대상이 된다.
    - @RestController: 컴포넌트 스캔과 스프링 Rest 컨트롤러로 인식된다.

### 로그 추적기 - 요구사항 분석
- 요구사항
  - 모든 PUBLIC 메서드의 호출과 응답 정보를 로그로 출력
  - 애플리케이션의 흐름을 변경하면 안됨
    - 로그를 남긴다고 해서 비즈니스 로직의 동작에 영향을 주면 안됨
  - 메서드 호출에 걸린 시간
  - 정상 흐름과 예외 흐름 구분
    - 예외 발생시 예외 정보가 남아야 함
  - 메서드 호출의 깊이 표현
  - HTTP 요청을 구분
    - HTTP 요청 단위로 특정 ID를 남겨서 어떤 HTTP 요청에서 시작된 것인지 명확하게 구분이 가능해야 함
    - 트랜잭션 ID (DB 트랜잭션X), 여기서는 하나의 HTTP 요청이 시작해서 끝날 때 까지를 하나의 트랜잭션이라 함
  
### 로그 추적기 V1 - 프로토타입 개발
- 애플리케이션의 모든 로직에 직접 로그를 남겨도 되지만, 그것보다는 더 효율적인 개발 방법이 필요하다.
- 특히 트랜잭션ID와 깊이를 표현하는 방법은 기존 정보를 이어 받아야 하기 때문에 단순히 로그만 남긴다고 해결할 수 있는 것은 아니다.

#### TraceId 클래스
- 로그 추적기는 트랜젹선ID와 깊이를 표현하는 방법이 필요하다. 여기서는 트랜잭션ID와 깊이를 표현하는 level을 묶어서 'TraceId'라는 개념을 만들었다. 'TraceId'는 단순히 id(트랜잭션ID)와 level 정보를 함께 가지고 있다.
  ```
  [796bccd9] OrderController.request()  //트랜잭션ID: 796bccd9, level:0
  [796bccd9] |-->OrderService.orderItem()  //트랜잭션ID: 796bccd9, level:1
  [796bccd9] |   |-->OrderRepository.save()  //트랜잭션ID: 796bccd9, level:2
  ```

#### UUID
- TraceId를 처음 생성하면 createdId()를 사용해서 UUID를 만들어낸다. UUDI가 너무 길어서 여기서는 앞 8자리만 사용한다. 이정도면 로그를 충분히 구분할 수 있다. 여기서는 이렇게 만들어진 값을 트랜잭션ID로 사용한다.
  ```
  ab99e16f-3cde-4d24-8241-256108c203a2 //생성된 UUID
  ab99e16f //앞 8자리만 사용
  ```
  
#### createNextId()
- 다음 TraceId를 만든다. 예제 로그를 잘 보면 깊이가 증가해도 트랜잭션ID는 같다. 대신에 깊이가 하나 증가한다.
- 실행 코드: new TraceId(id, level+1)
  ```
  [796bccd9] OrderController.request()  
  [796bccd9] |-->OrderService.orderItem()  //트랜잭션ID가 같다. 깊이는 하나 증가한다.
  ```
- 따라서 createdNextId()를 사용해서 현재 TraceId를 기반으로 다음 TraceId를 만들면 id는 기존과 같고, level은 하나 증가한다.

#### createdPreviousId()
- createdNextId()의 반대 역할을 한다. id는 기존과 같고, level은 하나 감소한다.

#### isFirstLevel()
- 첫 번째 레벨 여부를 편리하게 확인할 수 있는 메서드

#### TraceStatus 클래스: 로그의 상태 정보를 나타낸다
- 로그를 시작하면 끝이 있어야 한다.
  ```
  [796bccd9] OrderController.request()  //로그 시작
  [796bccd9] OrderController.request() time=1016ms  //로그 종료
  ```
- TraceStatus는 로그를 시작할 때의 상태 정보를 가지고 있다. 이 상태 정보는 로그를 종료할 때 사용된다.
- traceId: 내부에 트랜잭션ID와 level을 가지고 있다.
- startTimeMs: 로그 시작시간이다. 로그 종료시 이 시작 시간을 기준으로 시작~종료까지 전체 수행 시간을 구할 수 있다.
- message: 시작시 사용한 메시지이다. 이후 로그 종료시에도 이 메시지를 사용해서 출력한다.

#### HelloTraceV1
- HelloTraceV1을 사용해서 실제 로그를 시작하고 종료할 수 있다. 그리고 로그를 출력하고 실행시간도 측정할 수 있다.

#### 공개 메서드 - 로그 추적기에서 사용되는 공개 메서드는 다음 3가지이다.
  - begin(..)
  - end(..)
  - exception(..)
     

  - TraceStatus begin(String message)
    - 로그를 시작한다.
    - 로그 메시지를 파라미터로 받아서 시작 로그를 출력한다.
    - 응답 결과로 현재 로그의 상태인 TraceStatus를 반환한다.
  - void end(TraceStatus status)
    - 로그를 정상 종료한다.
    - 파라미터로 시작 로그의 상태(TraceStatus)를 전달받는다. 이 값을 확용해서 실행 시간을 계산하고, 종료일에도 시작할 때와 동일한 로그 메시지를 출력할 수 있다.
    - 정상 흐름에서 호출한다.
  - void exception(TraceStatus status, Exception e)
    - 로그를 예외 상황으로 종료한다.
    - TraceStatus, Exception 정보를 함께 전달 받아서 실행시간, 예외 정보를 포함한 결과 로그를 출력한다.
    - 예외가 발생했을 때 호출한다.

#### 비공개 메서드
- complete(TraceStatus status, Exception e)
  - end(), exception()의 요청 흐름을 한곳에서 편리하게 처리한다. 실행 시간을 측정하고 로그를 남긴다.
- String addSpace(String prefix, int level): 다음과 같은 결과를 출력한다.
  ```
  prefix: -->
    level 0:
    level 1: |-->
    level 2: | |-->
  prefix: <--
    level 0:
    level 1: |<--
    level 2: | |<--
  prefix: <X-
    level 0:
    level 1: |<X-
    level 2: | |<X-
  ```
  

### 로그 추적기 V1 - 적용
- HelloTraceV1 trace : HelloTraceV1 을 주입 받는다. 참고로 HelloTraceV1 은 @Component
애노테이션을 가지고 있기 때문에 컴포넌트 스캔의 대상이 된다. 따라서 자동으로 스프링 빈으로 등록된다.
- trace.begin("OrderController.request()") : 로그를 시작할 때 메시지 이름으로 컨트롤러 이름 +
메서드 이름을 주었다. 이렇게 하면 어떤 컨트롤러와 메서드가 호출되었는지 로그로 편리하게 확인할 수 있다.
- 단순하게 trace.begin() , trace.end() 코드 두 줄만 적용하면 될 줄 알았지만, 실상은 그렇지 않다.
trace.exception() 으로 예외까지 처리해야 하므로 지저분한 try , catch 코드가 추가된다.
- begin() 의 결과 값으로 받은 TraceStatus status 값을 end() , exception() 에 넘겨야 한다. 결국
try , catch 블록 모두에 이 값을 넘겨야한다. 따라서 try 상위에 TraceStatus status 코드를
선언해야 한다. 만약 try 안에서 TraceStatus status 를 선언하면 try 블록안에서만 해당 변수가
유효하기 때문에 catch 블록에 넘길 수 없다. 따라서 컴파일 오류가 발생한다.
- throw e : 예외를 꼭 다시 던져주어야 한다. 그렇지 않으면 여기서 예외를 먹어버리고, 이후에 정상
흐름으로 동작한다. 로그는 애플리케이션에 흐름에 영향을 주면 안된다. 로그 때문에 예외가 사라지면
안된다.
  ![v1traceId](https://user-images.githubusercontent.com/62706198/180891273-0ef91b21-8099-48ac-b815-ebb85157732b.PNG)

#### 로그 추적기 V1을 통해 해결한 문제
- 모든 PUBLIC 메서드의 호출과 응답 정보 로그를 출력
- 애플리케이션의 흐름을 변경하면 안됨
  - 로그를 남긴다고 해서 비즈니스 로직의 동작에 영향을 주면 안됨
- 메서드 호출에 걸린 시간
- 정상 흐름과 예외 흐름 구분
  - 예외 발생시 예외 정보가 남아야 함
    
### 로그 추적기 V2 - 파라미터로 동기화 개발
- 트랜잭션ID와 메서드 호출의 깊이를 표현하는 가장 단순한 방법은 첫 로그에서 사용한 트랜잭션ID와 level을 다음 로그에 넘겨주면 된다.
- 현재 로그의 상태 정보인 트랜잭션ID와 level은 TraceId에 포함되어 있다. 따라서 TraceId를 다음 로그에 넘겨주면 된다.
#### beginSync(..)
- 기존 TraceId에서 createNextId()를 통해 다음 ID를 구한다.
- createNextId()의 TraceId 생성 로직은 다음과 같다.
  - 트랜잭션ID는 기존과 같이 유지한다.
  - 깊이를 표현하는 Level은 하나 증가한다. (0 -> 1)

- 처음에는 begin(..)을 사용하고, 이후에는 beginSync(..)를 사용하면 된다. beginSync(..)를 호출할 때 직전 로그의 traceId 정보를 넘겨주어야 한다.

#### begin_end_level2()- 실행로그
```
[0314baf6] hello1
[0314baf6] |-->hello2
[0314baf6] |<--hello2 time=2ms
[0314baf6] hello1 time=25ms
```
- 실행 로그를 보면 같은 트랜잭션ID를 유지하고 level을 통해 메서드 호출의 깊이를 표현하는 것을 확인할 수 있다.


### 로그 추적기 V2 - 적용
- 메서드 호출의 깊이를 표현하고, HTTP 요청도 구분하려면 처음 로그를 남기는 OrderController.request()에서 로그를 남길 때 어떤 깊이와 어떤 트랜잭션 ID를 사용했는지 다음 차례인 OrderServiceItem()에서 로그를 남기는 시점에 알아야한다.
- 결국 현재 로그의 상태 정보인 트랜잭션ID와 level이 다음으로 전달되어야 한다. 이 정보는 TraceStatus.traceId에 담겨있따. 따라서 traceId를 컨트롤러에서 서비스를 호출할 때 넘겨주면 된다.
  ![traceId](https://user-images.githubusercontent.com/62706198/180890196-aadc5ef4-2dc1-415e-9ff7-9f92862731aa.PNG)

#### 로그 추적기 v2을 통해 해결한 문제
- 메서드 호출의 깊이 표현
- HTTP 요청을 구분
  - HTTP 요청 단위로 특정 ID를 남겨서 어떤 HTTP 요청에서 시작된 것인지 명확하게 구분이 가능해야 함
  - 트랜잭션 ID (DB 트랜잭션X)
  
#### 남은 문제
- HTTP 요청을 구분하고 깊이를 표현하기 위해서 TraceId 동기화가 필요하다.
- TraceId의 동기화를 위해서 관련 메서드의 모든 파라미터를 수정해야 한다.
  - 만약 인터페이스가 있다면 인터페이스까지 모두 고쳐야 하는 상황이다.
- 로그를 처음 시작할 때는 begin()을 호출하고, 처음이 아닐 때는 beginSync()를 호출해야 한다.
  - 만약에 컨트롤러를 통해서 서비스를 호출하는 것이 아니라, 다른 곳에서 서비스를 처음으로 호출하는 상황이라면 넘길 TraceId가 없다.
  
## 쓰레드 로컬 - ThreadLocal

### 필드 동기화 - 개발
- 앞서 로그 추적기를 만들면서 다음 로그를 출력할 때 트랜잭션ID와 level을 동기화 하는 문제가 있다. 이 문제를 해결하기 위해 TraceId를 파라미터로 넘기도록 구현했다. 이렇게 해서 동기화는 성공했지만, 로그를 출력하는 모든 메서드에 TraceId 파라미터를 추가해야 하는 문제가 발생했다.
- 이런 문제를 해결할 목적으로, 향후 다양한 구현체로 변경할 수 있도록 LogTrace 인터페이스를 먼저 만들고, 구현해보자.

#### FieldLogTrace
- 파라미터를 넘기지 않고 TraceId를 동기화 할 수 있는 구현체를 만들어보자.
- TraceId를 동기화 하는 부분만 파라미터를 사용하는 것에서 TraceId traceHolder 필드를 사용하도록 변경되었다.
- 직전 로그의 TraceId는 파라미터로 전달되는 것이 아니라 FieldLogTrace의 필드인 traceIdHolder에 저장된다.
- syncTraceId()
  - TraceId를 새로 만들거나 앞선 로그의 TraceId를 참고해서 동기화하고, level도 증가한다.
  - 최초 호출이면 TraceId를 새로 만든다.
  - 직전 로그가 있으면 해당 로그의 TraceId를 참고해서 동기화하고, level도 하나 증가한다.
  - 결과를 traceHolder에 보관한다.
- releaseTraceId()
  - 메서드를 추가로 호출할 때는 level이 하나 증가해야 하지만, 메서드 호출이 끝나면 level이 하나 감소해야 한다.
  - releaseTraceId()는 level을 하나 감소한다.
  - 만약 최초 호출(levle==0)이면 내부에서 관리하는 traceId를 제거한다.
    ```
    [c80f5dbb] OrderController.request() //syncTraceId(): 최초 호출 level=0
    [c80f5dbb] |-->OrderService.orderItem() //syncTraceId(): 직전 로그 있음 level=1 증가
    [c80f5dbb] | |-->OrderRepository.save() //syncTraceId(): 직전 로그 있음 level=2 증가
    [c80f5dbb] | |<--OrderRepository.save() time=1005ms //releaseTraceId(): level=2->1 감소
    [c80f5dbb] |<--OrderService.orderItem() time=1014ms //releaseTraceId(): level=1->0 감소
    [c80f5dbb] OrderController.request() time=1017ms //releaseTraceId(): level==0, traceId 제거
    ```
- 실행 결과를 보면 트랜잭션ID 도 동일하게 나오고, level 을 통한 깊이도 잘 표현된다.
- FieldLogTrace.traceIdHolder 필드를 사용해서 TraceId가 잘 동기화 되는 것을 확인할 수 있다. 이제 불필요하게 TraceId를 파라미터로 전달하지 않아도 되고, 애플리케이션의 메서드 파라미터도 변경하지 않아도 된다.

### 필드 동기화 - 적용
- LogTrace 스프링 빈 등록
  - FieldLogTrace를 수동으로 스프링 빈으로 등록하자. 수동으로 등록하면 향후 구현체를 편리하게 변경할 수 있다는 장점이 있다.
  ```
  [f8477cfc] OrderController.request()
  [f8477cfc] |-->OrderService.orderItem()
  [f8477cfc] | |-->OrderRepository.save()
  [f8477cfc] | |<--OrderRepository.save() time=1004ms
  [f8477cfc] |<--OrderService.orderItem() time=1006ms
  [f8477cfc] OrderController.request() time=1007ms
  ```
- 파라미터 추가 없는 깔끔한 로그 추적기를 완성했다. 이제 실제 서비스에 배포한다고 가정해보자.

### 필드 동기화 - 동시성 문제
- 테스트 할 때는 문제가 없는 것 처럼 보인 FieldLogTrace는 심각한 동시성 문제를 가지고 있다.
  ```
  [nio-8080-exec-3] [aaaaaaaa] OrderController.request()
  [nio-8080-exec-3] [aaaaaaaa] |-->OrderService.orderItem()
  [nio-8080-exec-3] [aaaaaaaa] | |-->OrderRepository.save()
  [nio-8080-exec-4] [aaaaaaaa] | | |-->OrderController.request()
  [nio-8080-exec-4] [aaaaaaaa] | | | |-->OrderService.orderItem()
  [nio-8080-exec-4] [aaaaaaaa] | | | | |-->OrderRepository.save()
  [nio-8080-exec-3] [aaaaaaaa] | |<--OrderRepository.save() time=1005ms
  [nio-8080-exec-3] [aaaaaaaa] |<--OrderService.orderItem() time=1005ms
  [nio-8080-exec-3] [aaaaaaaa] OrderController.request() time=1005ms
  [nio-8080-exec-4] [aaaaaaaa] | | | | |<--OrderRepository.save()
  time=1005ms
  [nio-8080-exec-4] [aaaaaaaa] | | | |<--OrderService.orderItem()
  time=1005ms
  [nio-8080-exec-4] [aaaaaaaa] | | |<--OrderController.request() time=1005ms
  ```
- 동시에 여러 사용자가 요청하면 여러 쓰레드가 동시에 애플리케이션 로직을 호출하게 된다. 따라서 로그는 이렇게 섞여서 출력된다.

#### 동시성 문제
- FieldLogTrace는 싱글톤으로 등록된 스프링 빈이다. 이 객체의 인스턴스가 애플리케이션에 딱 1개 존재한다는 뜻이다. 이렇게 하나만 있는 인스턴스의 FieldLogTrace.traceHolder 필드를 여러 쓰레드가 동시에 접근하기 때문에 문제가 발생한다.

### 동시성 문제 - 예제 코드

#### 순서대로 실행
- sleep(2000)을 설정해서 thread-A의 실행이 끝나고 나서 thread-B가 실행되도록 해보자. FieldService.logic() 메서드는 내부에 sleep(1000)으로 1초의 지연이 있다. 따라서 1초 이후에 호출하면 순서대로 실행할 수 있다.
  ```
  sleep(2000);  //동시성 문제 발생X
  //sleep(100);  //동시성 문제 발생O
  ```
- 실행 결과  
  ```
  [Test worker] main start
  [Thread-A] 저장 name=userA -> nameStore=null
  [Thread-A] 조회 nameStore=userA
  [Thread-B] 저장 name=userB -> nameStore=userA
  [Thread-B] 조회 nameStore=userB
  [Test worker] main exit
  ```
  - Thread-A는 userA를 nameStore에 저장했다.
  - Thread-A는 userA를 nameStore에서 조회했다.
  - Thread-B는 userB를 nameStore에 저장했다.
  - Thread-B는 userB를 nameStore에서 조회했다.
#### 동시성 문제 발생 코드
- 이번에는 sleep(100)을 설정해서 thread-A의 작업이 끝나기 전에 thread-B가 실행되도록 해보자. 참고로 FieldService.logic() 메서드는 내부에 sleep(1000)으로 1초의 지연이 있다. 따라서 1초 이후에 호출하면 순서대로 실행할 수 있따. 다음에 설정할 100(ms)는 0.1초이기 때문에 thread-A의 작업이 끝나기 전에 thread-B가 실행된다.
  ```
  //sleep(2000);  //동시성 문제 발생X
  sleep(100);  //동시성 문제 발생O
  ```
  
- 실행 결과
  ```
  [Test worker] main start
  [Thread-A] 저장 name=userA -> nameStore=null
  [Thread-B] 저장 name=userB -> nameStore=userA
  [Thread-A] 조회 nameStore=userB
  [Thread-B] 조회 nameStore=userB
  [Test worker] main exit
  ```
  - Thread-A는 userA를 nameStore에 저장했다.
  - Thread-B는 userB를 nameStore에 저장했다.
  - Thread-A는 userB를 nameStore에서 조회했다.
  - Thread-B는 userB를 nameStore에서 조회했다.
  
#### 동시성 문제
- 결과적으로 Thread-A 입장에서는 저장한 데이터와 조회한 데이터가 다른 문제가 발생한다. 이처럼 여러 쓰레드가 동시에 같은 인스턴스의 필드 값을 변경하면서 발생하는 문제를 동시성 문제라 한다.
이런 동시성 문제는 여러 쓰레드가 같은 인스턴스의 필드에 접근해야 하기 때문에 트래픽이 적은 상황에서는 확율상 잘 나타나지 않고, 트래픽이 점점 많아질 수록 자주 발생한다. 특히 스프링 빈 처럼 싱글톤 객체의 필드를 변경하며 사용할 때 이러한 동시성 문제를 조심해야 한다.
  
#### 참고
- 이런 동시성 문제는 지역 변수에서는 발생하지 않는다. 지역 변수는 쓰레드마다 각각 다른 메모리 영역이 할당된다.
동시성 문제가 발생하는 곳은 같은 인스턴스의 필드(주로 싱글톤에서 자주 발생), 또는 static 같은 공용 필드에 접근할 때 발생한다. 동시성 문제는 값을 읽기만 하면 발생하지 않는다. 어디선가 값을 변경하기 때문에 발생한다.
  
### ThreadLocal - 소개
- 쓰레드 로컬은 해당 쓰레드만 접근할 수 있는 특별한 저장소를 말한다.
- 여러 사람들이 같은 물건 보관 창구를 사용하더라도 창구 직원은 사용자를 인식해서 사용자별로 확실하게 물건을 구분해준다.
- 일반적인 변수 필드
  - 여러 쓰레드가 같은 인스턴스의 필드에 접근하면 처음 쓰레드가 보관한 데이터가 사라질 수 있다.
- 쓰레드 로컬
  - 쓰레드 로컬을 사용하면 각 쓰레드마다 별도의 내부 저장소를 제공한다. 따라서 같은 인스턴스의 쓰레드 로컬 필드에 접근해도 문제 없다.
  
### Thread Local - 예제 코드
#### ThreadLocal 사용법
- 값 저장: ThreadLocal.set(xxx)
- 값 조회: ThreadLocal.get()
- 값 제거: ThreadLocal.remove()
#### 주의
- 해당 쓰레드가 쓰레드 로컬을 모두 사용하고 나면 ThreadLocal.remove()를 호출해서 쓰레드 로컬에 저장된 값을 제거해주어야 한다.
- 실행 결과
  ```
  [Test worker] main start
  [Thread-A] 저장 name=userA -> nameStore=null
  [Thread-B] 저장 name=userB -> nameStore=null
  [Thread-A] 조회 nameStore=userA
  [Thread-B] 조회 nameStore=userB
  [Test worker] main exit
  ```
  
- 쓰레드 로컬 덕분에 쓰레드 마다 각각 별도의 데이터 저장소를 가지게 되었다. 결과적으로 동시성 문제도 해결되었다.

### 쓰레드 로컬 동기화 - 개발
- FieldLogTrace에서 발생했던 동시성 문제를 ThreadLocal로 해결해보자. TraceId traceIdHolder 필드를 쓰레드 로컬을 사용하도록 ThreadLocal<TraceId> traceIdHolder로 변경하면 된다.
- 필드 대신에 쓰레드 로컬을 사용해서 데이터를 동기화하는 ThreadLocalLogTrace를 새로 만들자.

#### ThreadLocal.remove()
- 추가로 쓰레드 로컬을 모두 사용하고 나면 꼭 ThreadLocal.remove()를 호출해서 쓰레드 로컬에 저장된 값을 제거해주어야 한다.
- traceId.isFirsLevel() (level==0)인 경우 ThreadLocal.remove()를 호출해서 쓰레드 로컬에 저장된 값을 제거해준다.

### 쓰레드 로컬 - 주의사항
- 쓰레드 로컬의 값을 사용 후 제거하지 않고 그냥 두면 WAS(톰캣)처럼 쓰레드 툴을 사용하는 경우에 심각한 문제가 발생할 수 있다.

#### 사용자A 저장 요청
![thread1](https://user-images.githubusercontent.com/62706198/182196919-e2336593-bda3-456e-b633-7ae254547b5d.JPG)
1. 사용자A가 저장 HTTP를 요청했다.
2. WAS는 쓰레드 풀에서 쓰레드를 하나 조회한다.
3. 쓰레드 thread-A가 할당되었따.
4. thread-A는 사용자A의 데이터를 쓰레드 로컬에 저장한다.
5. 쓰레드 로컬의 thread-A 전용 보관소에 사용자A 데이터를 보관한다.

#### 사용자A 저장 요청 종료
![thread2](https://user-images.githubusercontent.com/62706198/182197002-6b2aa73c-779f-4ab7-84cc-23d4528973a3.JPG)
1. 사용자A의 HTTP응답이 끝난다.
2. WAS는 사용이 끝난 thread-A를 쓰레드 풀에 반환한다. 쓰레드를 생성하는 비용은 비싸기 때문에 쓰레드를 제거하지 않고, 보통 쓰레드 풀을 통해서 쓰레드를 재사용한다.
3. thread-A는 쓰레드풀에 아직 살아있다. 따라서 쓰레드 로컬의 thread-A 전용 보관소에 사용자A 데이터도 함께 살아있게 된다.

#### 사용자B 조회 요청
![thread3](https://user-images.githubusercontent.com/62706198/182197074-6b8e79d2-8807-4fe8-b19b-bb0059e8a06c.JPG)
1. 사용자B가 조회를 위한 새로운 HTTP 요청을 한다.
2. WAS는 쓰레드 풀에서 쓰레드를 하나 조회한다.
3. 쓰레드 thread-A가 할당되었다. (물론 다른 쓰레드가 할당될 수도 있다.)
4. 이번에는 조회하는 요청이다. thread-A는 쓰레드 로콜에서 데이터를 조회한다.
5. 쓰레드 로컬은 thrad-A 전용 보관소에 있는 사용자A값을 반환한다.
6. 결과적으로 사용자A 값이 반환된다.
7. 사용자B는 사용자A의 정보를 조회하게 된다.

- 결과적으로 사용자B는 사용자A의 데이터를 확인하게 되는 심각한 문제가 발생하게 된다. 이런 문제를 예방하려면 사용자A의 요청이 끝날 때 쓰레드 로컬의 값을 ThreadLocal.remove()를 통해서 꼭 제거해야 한다.

## 템플릿 메서드 패턴과 콜백 패턴
### 템플릿 메서드 패턴 - 시작
#### 핵심 기능 vs 부가 기능
- 핵심 기능은 해당 객체가 제공하는 고유의 기능이다. 예를 들어서 orderService의 핵심 기능은 주문 로직이다. 메서드 단위로 보면 orderService.orderItem()의 핵심 기능은 주문 데이터를 저장하기 위해 리포지토리를 호출하는 orderRepository.save(itemId)코드가 핵심 기능이다.
- 부가 기능은 핵심 기능을 보조하기 위해 제공되는 기능이다. 예를 들어서 로그 추적 로직, 트랜잭션 기능이 있다. 이러한 부가 기능은 단독으로 사용되지는 않고, 핵심 기능과 함께 사용된다. 예를 들어서 로그 추적기능은 어떤 핵심 기능이 호출되었는지 로그를 남기기 위해 사용한다. 그러니까 핵심 기능을 보조하기 위해 존재한다.

#### 변하는 것과 변하지 않는 것을 분리
- 좋은 설계는 변하는 것과 변하지 않는 것을 분리하는 것이다.
- 여기서 핵심 기능 부분은 변하고, 로그 추적기를 사용하는 부분은 변하지 않는 부분이다. 이 둘을 분리해서 모듈화해야 한다.
- 템플릿 메서드 패턴(Template Method Pattern)은 이런 문제를 해결하는 디자인 패턴이다.

### 템플릿 메서드 패턴 - 예제1
```java
private void logic1() {
   long startTime = System.currentTimeMillis();
   //비즈니스 로직 실행
   log.info("비즈니스 로직1 실행");
   //비즈니스 로직 종료
   long endTime = System.currentTimeMillis();
   long resultTime = endTime - startTime;
   log.info("resultTime={}", resultTime);
 }
 private void logic2(){
    long startTime=System.currentTimeMillis();
    //비즈니스 로직 실행
    log.info("비즈니스 로직2 실행");
    //비즈니스 로직 종료
    long endTime=System.currentTimeMillis();
    long resultTime=endTime-startTime;
    log.info("resultTime={}",resultTime);
  }
```
- 변하는 부분: 비즈니스 로직
- 변하지 않는 부분: 시간 측정
- 템플릿 메서드 패턴을 사용해서 변하는 부분과 변하지 않는 부분을 분리해보자.

### 템플릿 메서드 패턴 - 예제2
![template](https://user-images.githubusercontent.com/62706198/183289621-a3f4f0af-dc72-4c91-98fe-786b64d800cf.JPG)
- 템플릿 메서드 패턴은 이름 그대로 템플릿을 사용하는 방식이다. 템플릿은 기준이 되는 거대한 틀이다. 템플릿이라는 틀에 변하지 않는 부분을 몰아둔다. 그리고 일부 변하는 부분을 별도로 호출해서 해결한다.
- 템플릿 메서드 패턴은 부모 클래스에 변하지 않는 템플릿 코드를 둔다. 그리고 변하는 부분은 자식 클래스에 두고 상속과 오버라이딩을 사용해서 처리한다.
  
#### 템플릿 메서드 패턴 인스턴스 호출 그림
![template2](https://user-images.githubusercontent.com/62706198/183290203-6480ebfa-4e16-4feb-b0dd-424d06c06bef.JPG)
- template1.execute()를 호출하면 템플릿 로직인 AbstractTemplate.execute()를 실행한다. 여기서 중간에 call() 메서드를 호출하는데, 이 부분이 오버라이딩 되어있다. 따라서 현재 인스턴스인 SubClassLogic1 인스턴스의 SubClassLogic1.call()메서드가 호출된다.
- 템플릿 메서드 패턴은 이렇게 다형성을 사용해서 변하는 부분과 변하지 않는 부분을 분리하는 방법이다.

### 템플릿 메서드 패턴 - 예제3

#### 익명 내부 클래스 사용하기
- 템플릿 메서드 패턴은 SubClassLogic1, SubClassLogic2()처럼 클래스를 계속 만들어야 하는 단점이 있다. 익명 내부 클래스를 사용하면 이런 단점을 보완할 수 있다.
- 익명 내부 클래스를 사용하면 객체 인스턴스를 생성하면서 동시에 생성할 클래스를 상속 받은 자식 클래스를 정의할 수 있다. 이 클래스는 SubClassLogic1처럼 직접 지정하는 이름이 없고 클래스 내부에 선언되는 클래스여서 익명 내부 클래스라 한다.


### 템플릿 메서드 패턴 - 적용1
- AbstractTemplate은 템플릿 메서드 패턴에서 부모 클래스이고, 템플릿 역할을 한다.
- <T> 제네릭을 사용했다. 반환 타입을 정의한다.
- 객체를 생성할 때 내부에서 사용할 LogTrace trace를 전달 받는다.
- 로그에 출력할 message를 외부에서 파라미터로 전달받는다.
- 템플릿 코드 중간에 call() 메서드를 통해서 변하는 부분을 처리한다.
- abstract T call()은 변하는 부분을 처리하는 메서드이다. 이 부분은 상속으로 구현해야 한다.

### 템플릿 메서드 패턴 - 적용2
- 템플릿 메서드 패턴 덕분에 변하는 코드와 변하지 않는 코드를 명확하게 분리했다. 로그를 출력하는 템플릿 역할을 하는 변하지 않는 코드는 모두 AbstractTemplate에 담아두고, 변하는 코드는 자식 클래스를 만들어서 분리했다.
- OrderServiceV0: 핵심 기능만 있다.
- OrderServiceV3: 핵심 기능과 부가 기능이 함께 섞여있다.
- OrderServiceV4: 핵심 기능과 템플릿을 호출하는 코드가 섞여있다.
- V4는 템플릿 메서드 패턴을 사용한 덕분에 핵심 기능에 좀 더 집중할 수 있게 되었다.
#### 좋은 설계란?
- 진정한 좋은 설계는 바로 "변경"이 일어날 때 자연스럽게 드러난다.
- 지금까지 로그를 남기는 부분을 모아서 하나로 모듈화하고, 비즈니스 로직 부분을 분리했다. 여기서 만약 로그를 남기는 로직을 변경해야 한다고 생각해보자. 그래서 AbstractTemplate 코드를 변경해야 한다 가정해보자. 단순히 AbstractTemplate코드만 변경하면 된다.
- 템플릿이 없는 V3 상태에서 로그를 남기는 로직을 변경해야 한다고 생각해보자. 이 경우 모든 클래스를 다 찾아서 고쳐야 한다. 클래스가 수백 개라면 생각만해도 끔찍하다.

#### 단일 책임 원칙(SRP)
- V4는 단순히 템플릿 메서드 패턴을 적용해서 소스코드 몇줄을 줄인 것이 전부가 아니다. 로그를 남기는 부분에 단일 책임 원칙(SRP)를 지킨 것이다. 변경 지점을 하나로 모아서 변경에 쉽게 대처할 수 있는 구조를 만든 것이다.

### 템플릿 메서드 패턴 - 정의
- "작업에서 알고리즘의 골격을 정의하고 일부 단계를 하위 클래스로 연기합니다. 템플릿 메서드를 사용하면 하위 클래스가 알고리즘의 구조를 변경하지 않고도 알고리즘의 특정 단계를 재정의 할 수 있습니다." [GOF]
- 부모 클래스에 알고리즘의 골격인 템플릿을 정의하고, 일부 변경되는 로직은 자식 클래스에 정의하는 것이다. 이렇게 하면 자식 클래스가 알고리즘의 전체 구조를 변경하지 않고, 특정 부분만 재정의할 수 있다. 결국 상속과 오버라이딩을 통한 다형성으로 문제를 해결하는 것이다.
- 하지만, 템플릿 메서드 패턴은 상속을 사용한다. 따라서 상속에서 오는 단점들을 그대로 안고간다 .특히 자식 클래스가 부모 클래스와 컴파일 시점에 강하게 결합되는 문제가 있다. 이것은 의존관계에 대한 문제이다. 자식 클래스 입장에서는 부모 클래스의 기능을 전혀 사용하지 않는다. 그럼에도 불구하고 템플릿 메서드 패턴을 위해 자식 클래스는 부모 클래스를 상속 받고 있다.
- 상속을 받는 다는 것은 특정 부모 클래스를 의존하고 있다는 것이다. 자식 클래스의 extends 다음에 바로 부모 클래스가 코드상에 지정되어 있다. 따라서 부모 클래스의 기능을 사용하든 사용하지 않든 간에 부모 클래스를 강하게 의존하게 된다. 여기서 강하게 의존한다는 뜻은 자식 클래스의 코드에 부모 클래스의 코드가 명확하게 적혀 있다는 뜻이다. UML에서 상쇽을 받으면 삼각형 화살표가 자식 -> 부모를 향하고 있는 것은 이런 의존관계를 반영하는 것이다.
- 자식 클래스 입장에서는 부모 클래스의 기능을 전혀 사용하지 않는데, 부모 클래스를 알아야한다. 이것은 좋은 설계가 아니다. 그리고 이런 잘못된 의존관계 때문에 부모 클래스를 수정하면, 자식 클래스에도 영향을 줄 수 있다.
- 추가로 템플릿 메서드 패턴은 상속 구조를 사용하기 때문에, 별도의 클래스나 익명 내부 클래스를 만들어야 하는 부분도 복잡하다.

- 템플릿 메서드 패턴과 비슷한 역할을 하면서 상속의 단점을 제거할 수 있는 디자인 패턴이 바로 전략 패턴(Strategy Pattern)이다.

### 전략 패턴 - 예제1
- 탬플릿 메서드 패턴은 부모 클래스에 변하지 않는 템플릿을 두고, 변하는 부분을 자식 클래스에 두어서 상속을 사용해서 문제를 해결했다. 
- 전략 패턴은 변하지 않는 부분을 Context라는 곳에 두고, 변하는 부분을 Strategy라는 인터페이스를 만들고 해당 인터페이스를 구현하도록 해서 문제를 해결한다. 상속이 아니라 위임으로 문제를 해결하는 것이다.
- 전략 패턴에서 Context는 변하지 않는 템플릿 역할을 하고, Strategy는 변하는 알고리즘 역할을 한다.

- GOF 디자인 패턴에서 정의한 전략 패턴의 의도는 다음과 같다.
  - 알고리즘 제품군을 정의하고 각각을 캡슐화하여 상호 교환 가능하게 만들자. 전략을 사용하면 알고리즘을 사용하는 클라이언트와 독립적으로 알고리즘을 변경할 수 있다.
    ![strategy](https://user-images.githubusercontent.com/62706198/183436048-2add18a4-897e-4b0b-bd64-f054d158e1d8.JPG)
  
#### ContextV1
- ContextV1은 변하지 않는 로직을 가지고 있는 템플릿 역할을 하는 코드이다. 전략 패턴에서는 이것을 컨텍스트(문맥)이라 한다. 쉽게 이야기 해서 컨텍스트(문맥)은 크게 변하지 않지만, 그 문맥 속에서 strategy를 통해 일부 전략이 변경된다 생각하면 된다.
- Context는 내부에 Strategy strategy 필드를 가지고 있다. 이 필드에 변하는 부분인 Strategy의 구현체를 주입하면 된다.
- 전략 패턴의 핵심은 Context는 Strategy 인터페이스에만 의존한다는 점이다. 덕분에 Strategy의 구현체를 변경하거나 새로 만들어도 Context 코드에는 영향을 주지 않는다.
- 스프링에서 의존관계 주입에서 사용하는 방식이 바로 전략 패턴이다.

#### 전략 패턴 실행 그림
  ![strategy2](https://user-images.githubusercontent.com/62706198/183439297-3821727c-6515-4bc9-9000-c2e4fe6bc650.JPG)
1. Context에 원하는 Strategy 구현체를 주입한다.
2. 클라이언트는 context를 실행한다.
3. context는 context로직을 시작한다.
4. context 로직 중간에 strategy.call()을 호출해서 주입 받은 strategy 로직을 실행한다.
5. context는 나머지 로직을 실행한다.


### 전략 패턴 - 예제2
- 전략 패턴도 익명 내부 클래스를 사용할 수 있다.
  
- 변하지 않는 부분을 Context에 두고 변하는 부분을 Strategy를 구현해서 만든다. 그리고 Context의 내부 필드에 Strategy를 주입해서 사용했다.

### 선 조립, 후 실행
- 여기서 이야기하고 싶은 부분은 Context의 내부 필드에 Strategy를 두고 사용하는 부분이다. 이 방식은 Context와 Strategy를 실행 전에 원하는 모양으로 조립해두고, 그 다음에 Context를 실행하는 선 조립, 후 실행 방식에서 매우 유용하다.
- Context와 Strategy를 한번 조립하고 나면 이후로는 Context를 실행하기만 하면 된다. 우리가 스프링으로 애플리케이션을 개발할 때 애플리케이션 로딩 시점에 의존관계 주입을 통해 필요한 의존관계를 모두 맺어두고 난 다음에 실제 요청을 처리하는 것과 같은 원리이다. 
- 이 방식의 단점은 Context와 Strategy를 조립한 이후에는 전략을 변경하기가 번거롭다는 점이다. 물론 Context에 setter를 제공해서 Strategy를 넘겨 받아 변경하면 되지만, Context를 싱글톤으로 사용할 때는 동시성 이슈 등 고려할 점이 많다. 그래서 전략을 실시간으로 변경해야 하면 차라리 이전에 개발한 테스트 코드 처럼 Context를 하나 더 생성하고 그곳에 다른 Strategy를 주입하는 것이 더 나은 선택일 수 있다.

### 전략 패턴 - 예제3
- 이전에는 Context의 필드에 Strategy를 주입해서 사용했다. 이번에는 전략을 실행할 때 직접 파라미터로 전달해서 사용해보자.

- Context와 Strategy를 '선 조립 후 실행'하는 방식이 아니라 Context를 실행할 때 마다 전략을 인수로 전달한다.
- 클라이언트는 Context를 실행하는 시점에 원하는 Strategy를 전달할 수 있다. 따라서 이전 방식과 비교해서 원하는 전략을 더욱 유연하게 변경할 수 있다.
- 테스트 코드를 보면 하나의 Context만 생성한다. 그리고 하나의 Context에 실행 시점에 여러 전략을 인수로 전달해서 유연하게 실행하는 것을 확인할 수 있다.


#### 전략 패턴 파라미터 실행 그림
![strategy3](https://user-images.githubusercontent.com/62706198/184476749-a1fc821e-8eba-4c4b-b37f-39927752b443.JPG)
1. 클라이언트는 Context를 실행하면서 인수로 Strategy를 전달한다.
2. Context는 execute() 로직을 실행한다.
3. Context는 파라미터로 넘어온 strategy.call() 로직을 실행한다.
4. Context의 execute() 로직이 종료된다.

#### 정리
- ContextV1은 필드에 Strategy를 저장하는 방식으로 전략 패턴을 구사했다.
    - 선 조립, 후 실행 방법에 적합하다.
    - Context를 실행하는 시점에는 이미 조립이 끝났기 때문에 전략을 신경쓰지 않고 단순히 실행만 하면 된다.
- ContextV2는 파라미터에 Strategy를 전달받는 방식으로 전략 패턴을 구사했다.
    - 실행할 때 마다 전략을 유연하게 변경할 수 있다.
    - 단점 역시 실행할 때 마다 전략을 계속 지정해주어야 한다는 점이다.
    