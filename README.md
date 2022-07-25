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
