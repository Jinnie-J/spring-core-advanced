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




