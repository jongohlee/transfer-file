# Transfer File Agent

Transfer File Agent는 EAI, FEP,  Batch 등 단위시스템에  설치되어 서버간 파일 전송을 처리하는 TCP/IP 기반 서버 에이전트 입니다.   
 
 ### 기술 스택  ###

  - 자바로 작성되어 있으며 배포/ 실행 편의성을 위해 Spring-boot, Spring Framework를 사용하였습니다.  
  - 비동기 입출력 처리를 위해 Netty Framework를 사용하였습니다.
  -  빌드 배포와 의존성 관리를 위해 Gradle 을 사용하였습니다.

### 구성 ###

산출물은 Transfer File Agent와 Client library로 구성되어 있으며 각각 다음의 역할울 수행합니다.

-  Transfer File Agent : TCP/IP 서버로 단위 시스템에 설치되어 파일의 송수신을 처리합니다.
-  Client lIbrary : 단위 시스템에 라이브러리로 포함되거나 단일 클라이언트로 작성되어 다음 역할을 수행합니다.
   -  Agent로 파일을 전송
   - Agent에서 파일을 수신
   - Agent간의 파일 전송 명령을 전달
   - Agent의 상태를 확인하고 제어 명령을 전달


## Transfer Agent `(Server)` 설치 ##

프로젝트 Base Directory/dist에 포함된 배포 파일을 이용하여 File Transfer Agent를 설치합니다.

- transfer-file-${version}-${commitId}-prod.zip : 서버 에이전트 설치를 위한 라이브러리, 설정, 실행파일
- transfer-file-${version}-${commitId}-client.zip : 클라이언트 라이브러리와 테스트 도구

### Step 1 ###

설치 위치로 이동하여 *-prod.zip 파일의 압축을 해제합니다. 배포 파일은 설치위치에 다음의 디렉토리 구조를 생성합니다.

>```
>.
>..
>├── ${install_dirctrory}
>│   ├── config
>│   │   ├── transfer-file.yml
>│   │   ├── transfer-file.conf 
>│   │   ├── custom-context.xml
>│   ├── lib
>│   │   └── transfer-file.jar
>│   ├── README.md
>│   ├── agent-boot.sh
>│   ├── agent-boot.bat
>│   ├── transfer-file.service.exe
>│   ├── transfer-file.service.xml
>│   ├── transfer-file.NET2.exe  
>└─└── transfer-fileNET4.exe

 - transfer-file.yml : Agent Server를 위한 설정파일
 - transfer-file.conf : executable jar(transfer-file.jar)파일이 init.d Service로 등록되어 실행될 때 사용되는 환경변수 파일
 - custom-context.xml : 적용 환경에 맞게 커스터마이징된 Bean 등록 파일로 파일 전송간 선후처리기를 등록
 - transfer-file.jar : executable jar파일로 실행을 위해 필요한 클래스들과 의존 라이브러리들을 포함
 - agent-boot.sh : Linux System 환경에서 command line으로 Agent Server를 실행하기 위한 shell 파일
 - agent-boot.bat : Windows System 환경에서 command line으로 Agent Server를 실행하기 위한 배치 파일
 - transfer-file.service.exe : Windows System에 Agent Server를 서비스로 등록하기 위한 실행 파일(transfer-file.NET4.exe파일과 동일)
 - transfer-file.service.xml : Windows System에 Agent Server를 서비스로 등록하기 위한 설정 파일
 - transfer-file.NET2.exe : .NET2만을 지원하는 Windows 환경에서 사용되는 서비스 등록 실행 파일로 필요한 경우 `bxi-file-agent.service.exe`로 이름을 변경하여 사용
 - transfer-file.NET4.exe : .NET4를 지원하는 Windows 환경에서 사용되는 서비스 등록 실행 파일로 필요한 경우 `bxi-file-agent.service.exe`로 이름을 변경하여 사용

### Step 2 ###

./config/tranfer-file.yml파일을 수정하여 Agent Server 실행을 위해 필요한 설정 항목을 수정한다. 대부분의 경우 미리 작성되어 있는 내용을 그대로 적용합니다.

```yaml
spring:
  main:
    web-application-type: none
    banner-mode: "off"
  application:
    name: transfer-file
    
logging:
  file: logs/transfer-file.log
  level: 
    root: INFO
    org.springframework: INFO
    io.netty: INFO
    easymaster: DEBUG

context:
   location: file:./config # fixed, can not edit

management:
  endpoint:
    shutdown: 
      enabled: true
        
transfer:
   # 다른 Ageng로 연결할 때의 connection timeout
  connect-timeout: 5s
  # 대용량 파일 전송시에는 성능을 위해 파일을 분할하여 전송
  # 분할 전송이 완료된 후 merge request를 전달하여 분할 전송된 파일을 병합
  # 이 과정을 하나의 세션으로  처리하고 있으며 전체 과정 중 오류 및 지연이 발생하는 경우 
  # Session timeout을 두어 분할 전송된 파일들을 일괄 제거하고 전송 실패로 처리
  session-timeout: 30M
  # default 120 min, 요청별로 timeout seconds를 지정할 수 있으나 이를 지정하지 않은 경우 아래에 지정된 최대 허용 시간으로 사용한다.
  transfer-timeout: 120M
  # ssl 적용 여부를 지정
  ssl: on
  bind: 127.0.0.1
  tcp-port: 8024
  boss-count: 1
  worker-count: 15
  keep-alive: true
  backlog: 100
  #하나의 파일 전송 메시지는 chunk단위로 나뉘어 전송
  # 아래에 chunk size를 지정
  chunk-size: 1048576 # 1M
  # Agent 실행 시 파일 저장소 위치를  확인하고 디렉토리를 생성
  # 파일 시스템 오류가 발생하는 경우 중단할 지 여부를 결정
  validation: on

  ##################################################################################
  #  repository 영역에는 다음 항목을 지정합니다.
  #    1. base-dir: 전송할 파일 위치와  수신된 파일을 저장하기 위한 기본 경로
  #    2. backup-dir: 파일 전송후 백업이 요청파라미터에 포함되었으나 백업 위치를 따로 지정하지 않은 경우 사용될 기본 백업 경로
  #  repository하위의 sites 설정에는 업무별로 기본 경로를 분리하여 Agent server를 운영해야 할 경우  경로 집합을 site별로 지정
  ##################################################################################
  repository:
    base-dir: ./repositories
    backup-dir: ${transfer.repository.base-dir}/backup
    sites:
      biz1:
        name: siteOfBiz1
        base-dir: ${transfer.repository.base-dir}/biz1
        backup-dir: ${transfer.repository.base-dir}/biz1/backup
      biz2:
        name: siteOfBiz2
        base-dir: ${transfer.repository.base-dir}/biz2
        backup-dir: ${transfer.repository.base-dir}/biz2/backup
      biz3:
        name: siteOfBiz3
        base-dir: ${transfer.repository.base-dir}/biz3
        backup-dir: ${transfer.repository.base-dir}/biz3/backup

```
## Transfer Agent `(Server)` 실행 과 운영 ##

Agent server는 command line 또는 시스템 서비스 형태로 실행될 수 있습니다.  배포된 패키지에는 운영환경에 따라 사용될 수 있는 command line control(start, stop, status) script와 시스템 서비스로 등록하여 운영할 때 사용할 수 있는 도구가 포함되어 있습니다.
### Windows System ###

Window System에서는 다음 Command Line 도구 또는 System Service로 등록하여 Agent Server를 실행시킬 수 있습니다.
#### Command Line 을 이용한 Agent Server 실행과 제어 ####

배포된 패키지에 포함된 `agent-boot.bat`파일을 이용하여 Agent server를 실행합니다. 

 -  Agent Server를 시작하기 위해서는 Windows에서 제공하는 Command Prompt (CMD)를 이용하여 설치 위치로 이동 후 아래 Command를 실행합니다.
 ``` bash
 agent-boot.bat start
 ``` 

- 다음 Command를 이용하여 실행 중인 Agent Server의 상태를 확인할 수 있습니다.
``` bash
agent-boot.bat status
```
 * 실행 중인 Agent Server는 다음 Command를 이용하여 중지할 수 있습니다.
``` bash
agent-boot.bat stop
```
#### Windows System Service를 이용한 Agent Server  실행과 제어 ####

배포된 패키지는 Windows System Service로 Agent Server를 등록하여 운영할 수 있도록 시스템 서비스 등록과 테스트, 등록 해제에 사용되는 아래 파일들이 포함되어 있습니다.

-  transfer-file.NET2.exe
-  transfer-file.NET4.exe
-  transfer-file.service.exe
-  transfer-file.service.xml

1. 시스템 서비스로 Agent Server 등록

```
transfer=file.service.exe install
```
목표 시스템이 .NET4를 지원하지 않는 경우 포함되어 있는 `transfer-file.NET2.exe`파일을 이용합니다.  `transfer-file.NET2.exe` 파일의 이름을 `transfer-file.service.exe`로 변경한 후 등록 명력을 실행합니다.   

2. Windows Service 관리도구를 이용하여 Agent Server 시작
 
  서비스 등록이 정상적으로 완료되면 `제어판->관리 도구->서비스`에서 정상 등록 여부룰 확인할 수 있으며 실행/중지 제어가 가능합니다.  서비스 등록을 위한 설정 파일 `transfer-file.service.xml`에 `startMode`가 `Automatic`으로 기본 설정되어 있고 `<delayedAutoStart/>`항목이  지정되어 있으므로 `transfer-file` Service는 Window System 시작과 함께 `지연시작` 됩니다.  `startMode`는 Boot, System, Automatic, Manual로 변경 지정될 수 있습니다.. 

  3. 시스템 서비스 제거

다음 작업을 통해 등록되어 있는 Transfer-file 서비스를 제거할 수 있습니다.

```
ransfer-file.service.exe uninstall
```
### Linux System ###

 Linux System에서는 다음 Command Line 도구 또는 init.d Service로 등록하여 Agent Server를 실행시킬 수 있습니다.
#### Command Line 을 이용한 Agent Server 실행과 제어 ####

배포된 패키지에 포함된 `agent-boot.sh`파일을 이용하여 Agent server를 실행합니다. 

 -  Agent Server를 시작하기 위해서는 Terminal을  이용하여 설치 위치로 이동 후 아래 Command를 실행합니다.
 ``` bash
 agent-boot.sh start
 ``` 

- 다음 Command를 이용하여 실행 중인 Agent Server의 상태를 확인할 수 있습니다.
``` bash
agent-boot.sh status
```
 * 실행 중인 Agent Server는 다음 Command를 이용하여 중지할 수 있습니다.
``` bash
agent-boot.sh stop
```
####  init.d Service를 이용한 Agent Server  실행과 제어 ####

배포된 패키지의 `transfer-file.jar` 파일은 fully executable jar 형태로 빌드되어 있으므로 symbolic link를 생성하여 System Service로 등록되고 실행될 수 있습니다.

1. init.d 서비스로 Agent Server 등록 

서비스 등록을 위해 root 권한으로 아래처럼 symbolic link를 생성합니다.

``` bash
sudo ln -s ${intall_directory}/lib/transfer-file.jar /etc/init.d/transfer-file
```  

서비스 등록 후에는 아래 처럼 Agent Server를 제어할 수 있습니다.

``` bash
service transfer-file {start|stop|force-stop|restart|force-reload|status|run}
```
### 환경변수 ###

Transfer Agent `(Server)`는 다양한 실행 환경을 제공함에 따라 JVM에 환경변수를 전달하는 여러 종류의 설정 파일이 사용됩니다.  아래는 실행 방법에 따라 maximum heap size 등을 수정하거나 JVM 실행 옵션을 추가해야 하는 경우 변경할 항목들입니다.

- Windows Command Line: JAVA_OPTS 변수 in transfer-file.bat
- Windows System Service: 전달 변수 in transfer-file.service.xml, 서비스 재등록 필요
- Linux Command Line: JAVA_OPTS 변수 in transfer-file.bat  
- Linux System Service: JAVA_OPTS 변수 in transfer-file.conf, 서비스 재등록 필요
### 로깅 ###

설치된 Agent Server가 실행되면 설치 위치의 하위에 `logs` 디렉토리가 생성되고 `transfer-file.log` 로그 파일과 프로세스 제어를 위해 필요한 `.lock`, `.pid`파일이 생성됩니다.  생성되는 로그 파일의 위치와 로깅 레벨은 `config/transfer--ile.yml`파일을 수정하여  변경할 수 있습니다. 

**과제 시연을 위한 벙법은 문서의 마지막에 설명합니다.**

- - -  

## Transfer File Agent의 기술적인 세부 내용 ##

Transfer File Agent에서는 클라이언트와 서버 또는 서버간 통신을 위해 HTTP와 유사한 별도의 프로토콜을 정의하였습니다. Agent 서버 사용시 기본적으로 제공되는 Java Client library를 사용하게 되므로 아래의 프로토콜에 대한 세부 사항을 이해할 필요는 없습니다. 그러나  메시지 프로토콜을 명시적으로 설계함으로써  이기종 언어로 Agent Server Cient를 작성할 수 있도록 확장성을 보장하였습니다.
## Frame Message Protocol ##

Agent Server의 요청과 응답 메시지는 다음과 같이 구성됩니다.

 **Messag Protocol Layout**
  
| Part   | Elements | Description |
|:----|:-----------------------------------------|:-----------------------------------------|
| COMMAND  | {GET\|PUT\|INFO\|ACTION\|TRANSFER} /uri   | 요청과 응답에 명시적으로 전달할 Command와 세부 명령을 표현할 Uri |
| PRE DEFINED HEADER  | Agent<br>Remote<br>Agent-Type<br>Response-Code<br>Reason<br>Connection<br>Content-Length<br>Deleted-Count<br>Transfer-Encoding<br>Transfer-Source-Uri<br>Transfer-Destination-Uri<br>Destination-Agent<br>Transfer-Validation<br>Transfer-Timeout-Seconds<br>Transfer-Interceptor<br>Merge-Resource<br>Session-Id<br>Resource-Length<br>Transferred-Resource    | 요청처리와 응답에 필요한 정보를 위해 미리 정의되어 있는 헤더 항목들 |
| CUSTOM HEADER   | ex) Request-AgentUser    |선후처리기에 전달되어 사용할 사용자 정의 헤더 항목들로 임의의 이름과 값으로 추가될 수 있음 |
| CONTENT  | bytes contents<br>...<br>      | 전송/수신 Contents |

- - -
### COMMANDS  와 URI ###

- INFO /health  : Agent Server에 Health 정보를 요청
-  INFO /info  : Agent Server에 시스템 정보를 요청
-  INFO /exist  : Agent Server에 특정 파일이 존재하는지 확인 요청
-  ACTION /session : 하나의 전송 트랜잭션이 대용량 파일의 분할 전송 처럼 순차 또는 병렬로 교환되는 여러 요청과 응답으로 처리되어야 하는 경우 완전한 처리를 보장하기 위해 사용될 session 생성을 Agent Server에 요청
-  ACTION /merge : 병렬로 분할 전송된 대용량 파일의 병합 요청
-  ACTION /shutdown : Agent Server Shutdown 요청
-  GET : Agent Server에 특정 파일의 송신을 요청, 클라이언트에서 직접 수신
-  PUT : 클라이언트에서 Agent Server에 특정 파일을 전송
-  LIST : Agent Server의 특정 위치에 존재하는 파일들의 목록을 요청
-  DELETE : Agent Server의 특정 위치에 존재하는 파일들의 삭제를 요청
-  TRANSFER : Agent Server의 특정 파일들을 다른 Agent Server에 전송하도록 요청

- - - 
 
### PRE DEFINED HEADERS ###

다음에서는 사용자에 의해 지정될 수 있는 주요 헤더 항목을 설명합니다.

| Element&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description | Examples |
|:---------------------|:---------------------|:---------------------------|
| Response-Code  | 응답에만 포함되는 요청에 대한 처리 결과 | 200= OK<br>210= Not Exist<br>400= Bad Request<br>402= Bad Response<br>404= Source File Not Found<br>405= Already Exist<br>500= Internal Server Error<br>510= Destination Not Responding<br>520= Transfer Failed<br>525= Delete Failed<br>530= File Permission Error<br>540= TimeOut Occurred |
| Reason  | 오류에 대한 결과 또는 정상 처리에 따른 정보 | Response-Code: 520 Transfer Failed<br>Reason: agent: [192.168.12.15:8024, 192.168.12.16:8024] <br>are not responding |
| Content-Length  | 전달되는 CONTENT영역의 크기 | Content-Length: 247549 |
| Transfer-Encoding  | Chunk 단위 전송 여부 | Transfer-Encoding: chunked |
| Content-Length  | 전달되는 CONTENT영역의 크기 | Content-Length: 247549 |
| Resource-Length  | EXIST command 요청의 결과로 파일이 존재하는 경우 해당 파일의 길이  | Reason: EXIST<br>Resource-Length: 557 |
| Deleted-Count  | DELETE command 요청에 따른 처리 결과 | Deleted-Count: 2<br>Reason: /agent/backup/20180717/check1.done<br>Reason: /agent/backup/20180717/check2.done |
| Transferred-Resource  | TRANSFER command 요청에 따라 source agent에서 target agent로 전송된 결과 | Reason: 2 files are transferred, 0 files are failed<br>Transferred-Resource: /agent/2432345.dat<br>Transferred-Resource: /agent/32453.dat |
| Transfer-Source-Uri  | 파일을 직접 전송하거나 다른 Agent로의 전송을 요청할 때 대상이 되는 리소스와 파마리터로 구성된 URI | Transfer-Source-Uri: agent://192.168.12.15:8025/fixed-content.txt?interceptor=simpleCustomTransferInterceptor&afterTransfer=backup |
| Transfer-Destination-Uri  | 파일을 직접 전송하거나 다른 Agent로의 전송을 요청할 때 수신위치에서 파일을 저장할 위치와 파마리터로 구성된 URI | Transfer-Destination-Uri: /backup/20101010/32345-content.bak?interceptor=simpleCustomReceiveInterceptor<br>&createAck=.done&onExist=overwriteOnExist |
| Transfer-Interceptor  | TRANSFER command와 함께 전달되는 선후처리 항목으로, 처리를 담당할 bean 등록 이름을 interceptor에 지정<br>URI에 Parameter로 포함된 선후처리기는 송수신 Agent위치에서 송신과 수신 선후에 실행되며 헤더에 포함된 선후처리  항목은 TRANSFER command 처리 선후에 실행된다. | Transfer-Interceptor: simpleCustomTransferInterceptor |
| Connection  | 요청측에서 Connection을 close해야 하는지 여부, 정보에 따라 closing은 자동으로 처리됨 | Response-Code: 520<br>Transfer Failed<br>Reason: agent: [192.168.12.15:8024, 192.168.12.16:8024]<br> are not responding<br>Connection: close |

- - - 

### URI 구성 ###

경로와 파일을 지정하기 위해 Header 항목(Transfer-Source-Uri, Transfer-Destination-Uri...)에 URI를 지정합니다. URI에는 전송 선후 처리(backup, delete, interceptor 실행 등)옵션을 지정하기 위한 parameter가 포함됩니다.

> ex) agent://192.168.219.141:8024/9063C6480000.dat?&createAck=.done&onExist=overwriteOnExist

리소스의 목록 요청과 삭제 명령에 사용되는 URI에는 ant style pattern 문자열이 지정될 수 있습니다.

> ex) agent://192.168.219.141:8024/backup/**/*

경로 URI에는 다음 항목들이 파라미터로 포함될 수 있습니다.

| Name | Description | Examples |
|:---------------------|:---------------------|:---------------------------|
| site  | 업무별로 site를 분리하여 운영 중인 경우 처리할 리소스의 site Id | site=accountMgmt |
| createAck  | 리소스를 수신하는 Agent에서 수신 완료 후 ACK 파일을 생성할 지  여부와 사용할 suffix | createAck=.done |
| afterTransfer  | 리소스 송신 Agent에서 송신 완료 후의 처리 방법 | afterTransfer=backup<br>afterTransfer=delete |
| interceptor  | command 처리 선후에 실행될 선후처리기 Bean의 이름 | interceptor=simpleCustomReceiveInterceptor |
| onExist  | 전송 대상 파일이 수신 Agent에 존재하는 경우 처리 방법 | onExist=overwriteOnExist<br>onExist=appendOnExist<br>onExist=failOnExist<br> |

URI에 포함되는 특수 문자를 인코딩하기 위해 API가 제공됩니다. 
 
``` java
String uri= TransferMessageUtil.encodedUri( "192.168.42.24", 8025, "d324234.dat",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParameter.param( SITE, "accountMgmt"));
```

- - -  
### Agent Client ###

서버에 실행 중인 Agent Server를 사용하는 어플리케이션에서는 위에 설명된 헤더 항목들을 모두 명시적으로 지정할 필요는 없으며 보다 원활히 사용될 수 있도록 Client API Set을 제공합니다.

클라이언트 배포 패키지는 `transfer-file-${version}-${commitId}-client.zip`로 제공되며 실행을 위해 필요한 의존 라이브러리를 포함하고 있습니다.

다음은 제공되는 주요 API에 대한 사용 샘플입니다..

1. Agent Server 연결

``` java
    // client 생성 후 연결을 생성하고 연결을 재사용하는 경우
    TransferClient client= TransferClient.create(  ${host}, ${port});
    Channel channel= null;

    try
    {
        channel= client.connect();
        Health health= client.requestHealth( channel);
        if( Status.UP.equals( health.getStatus()))
            boolean answer= client.requestResourceExist( channel, "/20201010/parallel-content.dat");
    }
    catch( Exception e){ logger.error( e.getMessage(), e); }
    finally
    {
        if( channel!= null) channel.close();
        client.shutdown();
    }

    // agentClient가 필요한 연결을 매번 생성하여 사용하도록 위임하는 경우  
    TransferClient client= TransferClient.create( ${host}, ${port});
    try { boolean answer= client.requestResourceExist( THROWAWAY, "/20201010/parallel-content.dat") ;}
    catch( Exception e){ logger.error( e.getMessage(), e);}
    finally{ client.shutdown();}
```
2. Agent Client APIs

``` java

    // 1. Agent Server의 실행환경 정보를 요청
    public Map<String, String> requestServerInfo( Channel channel) throws Exception
    public Map<String, String> requestServerInfo( Channel channel, long timeout) throws Exception

    // 2. Agent Server의 Health 상태를 확인
    public Health requestHealth( Channel channel) throws Exception
    public Health requestHealth( Channel channel, long timeout) throws Exception

    // 3. 특정 파일이 Agent Server의 특정 위치에 존재하는지 확인
    public boolean requestResourceExist( Channel channel, String path, String site) throws Exception
    public boolean requestResourceExist( Channel channel, String path, String site, long timeout) throws Exception

    // 4. Agent Server로부터 특정 파일을 요청하여 수신된 파일을 처리
    //    Functional Interface를 이용하여 응답 컨텐츠에 대한 처리를 작성
    public <R> R requestGetResource( Channel channel, String path, String site, Function<FileData, R> operator, OptionParam... options
     throws Exception
    public <R> R requestGetResource( Channel channel, String path, String site, Function<FileData, R> operator, long timeout, 
    OptionParam... options) throws Exception

    // example
    TransferClient client= TransferClient.create( ${host}, ${port});
    try
    {
        boolean answer= client.requestGetResource( THROWAWAY, "/20201010/parallel-content.dat",  "biz1", 
          content->{ 
                try
                {
                    content.renameTo( new File( "./get.dat"));
                    return true;
                }
                catch( IOException e){ return false;}
            });
      }
      catch( Exception e){ logger.error( e.getMessage(), e);}
      finally{ client.shutdown();}

    // 5. 특정 파일을 Agent Server에서 삭제
    //    처리 결과로 정상적으로 삭제된 리소스목록을 가져온다.
    public List<String> requestDeleteResources( Channel channel, String path, String site, OptionParam... options) throws Exception
    public List<String> requestDeleteResources( Channel channel, String path, String site, long timeout, OptionParam... options) throws Exception
            
    // 6. 경로와 패턴 문자열을 이용하여 Agent Server로부터 리소스의 목록을 조회
    public List<String> requestListResources( Channel channel, String path, String site, OptionParam... options) throws Exception
    public List<String> requestListResources( Channel channel, String path, String site, long timeout, OptionParam... options) throws Exception

    // 7. Agent Server에 특정 파일을 전송
    public boolean requestPutResource( Channel channel, File resource, String path, String site, OptionParam... options) throws Exception

    public boolean requestPutResource( Channel channel, File resource, String path, String site, long timeout, OptionParam... options) throws Exception

    // 8. 대형 파일을 병렬로 처리하여 Agent Server에 전송
    public boolean requestPutParallelResource( File resource, String path, String site, OptionParam... options) throws Exception
    
    // 병렬 처리로 파일을 전송할 때는 동시 처리를 위해 다음처럼 TransferClient 생성시에 적절한 worker count를 지정해주어야 합니다.
    TransferClient client= TransferClient.create( ${host}, ${port}, 10);
    try
    {
        File resource= ResourceUtils.getFile( "./src/test/resources/parallel-content.dat");
        boolean answer= client.requestPutParallelResource( resource, "/20201010/parallel-content.dat", "biz1",
            OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
            OptionParameter.param( CREATE_ACK, ".ack"),
            OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"));
    }
    catch( Exception e){ logger.error( e.getMessage(), e);}
    finally{ client.shutdown();} 

    // 9. 특정 Agent Server에 Transfer 명령을 전송하여 다른 Agent Server에 파일을 전송하도록 요청
    // sync 옵션이 true인 경우 전송 명령을 받은 Agent Server는 수신 Agetn로 전송을 완료 한 후 처리 결과를 응답합니다.
    // sync 옵션이 false인 경우 전송 명령을 받은 Agent Server는 수신 Agent로 전송을 시작한 후 처리 중인 내용을 응답합니다.
    public boolean requestTransferResources( Channel channel, List<TransferRequest> trans, boolean sync, OptionParam... options) throws Exception
    public boolean requestTransferResources( Channel channel, List<TransferRequest> trans, boolean sync, long timeout, OptionParam... options) throws Exception
            
   // example
    TransferClient client= TransferClient.create( ${host}, ${port}); 
    try
    {
        List<TransferRequest> trans= new ArrayList<TransferRequest>(); 
        trans.add( new TransferRequest()
            .from( client)
            .resource( "/20201010/parallel-content.large", OptionParamer.param( "biz1"))
            .to( ${host1}, ${port})
            .to( ${host2}, ${port}) // 수신 Agent Server가 여러 개 지정된 경우 첫번 째 Agent Server가 응답하지 않을 때 두번 째 Agent Server에 전송을 시도합니다.
            .path( "/20201010/parallel-content.dat",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST)));
        boolean answer= client.requestTransferResources( THROWAWAY, trans, true, -1,
                    OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"));
    }
    catch( Exception e){ logger.error( e.getMessage(), e); }
    finally{ client.shutdown(); }
    
    // 10. 원격 Agent Server를 shutdown
    public boolean requestShutdownCommand( Channel channel) throws Exception

    // 11. 범용적으로 사용할 수 있도록 ResponseComsumer Functional Interface를 인자로 받는 API
    // ResponseConsumer Function에서 응답으로 전달된 TransferMessage를 처리
    public <R> R request( Channel channel, TransferMessage request, ResponseConsumer<TransferMessage, R> consumer)
        throws RequestHandlerException, ResponseHandlerException

    public <R> R request( final Channel channel, TransferMessage request, ResponseConsumer<TransferMessage, R> consumer,
            long timeout) throws RequestHandlerException, ResponseHandlerException
            
```

- - -

### 사용자 지정 선후처리기 ###

**System Interceptors**

Agent Server에는 Server Instance별로 설정되어 모든 요청의 선후에 반드시 실행되는 선후 처리 Interceptor를 지정할 수 있습니다.
다음은 참고용으로 작성되어 배포 패키지에 포함되어 있는 custom system interceptor입니다.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="agentInterceptor" class="easymaster.transfer.file.interceptors.SimpleAgentInterceptor"/>
</beans>
```  

**Request Interceptors**

또한 요청별로 헤더(Transfer-Interceptor) 또는 URI Prameter에 지정되어 Agent Server간 송신 전후, 클라이언트에 송신 전후, 클라이언트로부터 수신 전후에 실행되는 선후처리기를 지정할 수 있습니다. 선후처리기 호출시 전달되는 TransferContext isntance에는 Spring ApplicationContext instance와 Request Header에 전달된 모든 항목 그리고 현재 처리 중인 파일이 있는 경우 파일의 절대 경로 정보들이 포함되어 있습니다.

다음은 참고용으로 작성되어 배포 패키지에 포함되어 있는 custom request interceptor입니다.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
    <!-- 송신 전후에 실행되는 Interceptor -->
    <bean id="simpleCustomTransferInterceptor" class="easymaster.transfer.file.interceptors.SimpleCustomTransferInterceptor"/>
    <!-- 수신 전후에 실행되는 Interceptor -->
    <bean id="simpleCustomReceiveInterceptor" class="easymaster.transfer.file.interceptors.SimpleCustomReceiveInterceptor"/>
</beans>
```  
### Chunked Message ###

과제 구현에서는 대용량 파일을 포함하여 파일이 송수신 되는 서버의 응답 시간을 개선하기 위해 데이터 송수신간에 Chunk단위 전송을 사용합니다.  

```java

public class TransferServerInitializer extends ChannelInitializer<SocketChannel>
{
    ...
     @Override
    protected void initChannel( SocketChannel channel) throws Exception
    {
        ...
        pipeLine
            .addLast( ZlibCodecFactory.newZlibDecoder( ZlibWrapper.GZIP))
            .addLast( ZlibCodecFactory.newZlibEncoder( ZlibWrapper.GZIP))
            .addLast( new TransferMessageServerCodec())
            .addLast( new ChunkedWriteHandler())
            .addLast( new TransferServerHandler( this.applicationContext, this.environment));
    } 
}

public class TransferChunkedContentEncoder implements ChunkedInput<TransferContent>
{
    ...
   @Override
    public TransferContent readChunk( ByteBufAllocator allocator) throws Exception
    {
        if( lastChunkSent)
            return null;

        if( progress>= content.definedLength())
        {
            lastChunkSent= true;
            content.release();
            return EMPTY_LAST_CONTENT;
        }

        int bufSize= (int)Math.min( chunkSize, content.definedLength()- progress);
        ByteBuf buffer= content.getChunk( bufSize);
        progress+= bufSize;

//        logger.debug( "readChunk current bufSize / progress: {} / {}", bufSize, progress);

        return new TransferContent( buffer);
    }
}
```
### SSL 적용 ###

안전한 연결과 데이터 송수신을 위해 설정을 이용하여 SSL 적용 여부를 지정할 수 있습니다.  이번 과제 구현에서는 self-signed 인증서를 사용하였으며 TransferClient에서 Insecure 인증서를 신뢰하도록 지정하였습니다. Root CA에 의해 서명된 인증서를 사용하기 위해서는  관련 소스의 변경이 필요합니다.

```yaml
transfer:
  ssl: on
```

```java
public class TransferServerConfiguration
{
    @Bean
    public ChannelInitializer<SocketChannel> channelInitializer( ApplicationContext applicationContext) throws Exception
    {
        SslContext sslContext= null;
        if( environment.isSsl())
        {
            SelfSignedCertificate ssc= new SelfSignedCertificate();
            sslContext= SslContextBuilder.forServer( ssc.certificate(), ssc.privateKey()).build();
        }
        return new TransferServerInitializer( applicationContext, environment, sslContext);
    }
}

public class TransferServerInitializer extends ChannelInitializer<SocketChannel>
{
    ...
    ...
     @Override
    protected void initChannel( SocketChannel channel) throws Exception
    {
        ChannelPipeline pipeLine= channel.pipeline();
        if( this.sslContext!= null)
            pipeLine.addLast( this.sslContext.newHandler( channel.alloc()));
        ,,,
    } 
}

public class TransferClient
{
    private boolean bootstrap()
    {
        bootstrap= new Bootstrap();
        bootstrap.group( workerGroup).channel( NioSocketChannel.class)
            .option( ChannelOption.SO_KEEPALIVE, true)
            .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeout)
            .handler( new ChannelInitializer<Channel>(){
                @Override
                protected void initChannel( Channel ch) throws Exception
                {
                    ChannelPipeline pipeline= ch.pipeline();
                    if( ssl)
                        pipeline.addLast( SslContextBuilder.forClient().trustManager( 
                                InsecureTrustManagerFactory.INSTANCE).build().newHandler( ch.alloc()));
                    ...
                }
            });
        return true;
    }

}
```
### 별도의 암복호화 처리 ###

별도의 암복화처리가 필요한 경우 위애 설명된 파일 송수신 간에 실생되는 선후처리기에서 암복화 모듈을 이용하여 처리할 수 있습니다.  암복화 처리에는 일반적으로 상용 암복화 모듈이 적용됩니다. 이번 과제에서는 

### 대용량 파일에 대한 병렬 전송 ###

### 수신 Agent Server의 복수 지정 ###

### 주요 로직에 대한 설명 ###

### 시연 내용과 방법 ###