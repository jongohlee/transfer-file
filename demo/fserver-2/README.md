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
- transfer-file-${version}-${commitId}-prod.zip : 클라이언트 라이브러리와 테스트 도구

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

## Transfer Agent `(Server)` 실행 ##

Agent server는 command line 또는 시스템 서비스 형태로 실행될 수 있습니다.  배포된 패키지에는 운영환경에 따라 사용될 수 있는 command line control(start, stop, status) script와 시스템 서비스로 등록하여 운영할 때 사용할 수 있는 도구가 포함되어 있습니다.
### Windows System ###

Window System에서는 다음 Command Line 도구 또는 System Service로 등록하여 Agent Server를 실행시킬 수 있습니다.
#### Command Line ####

배포된 패키지에 포함된 `agent-boot.bat`파일을 이용하여 Agent server를 실행합니다. 

 -  Agent Server를 시작하기 위해서는 Windows에서 제공하는 Command Prompt (CMD)를 이용하여 설치 위치로 이동 후 아래 Command를 실행합니다.
 ``` bash
 agent-boot.bat start
 ``` 

- 다음 Command를 이용하여 실행 중인 Agent Server의 상태를 확인할 수 있습니다.
``` bash
agent-boot.bat status
```
 * 실행 중인 Agent Server는 다음 Command를 이용하여 중지한다.
``` bash
agent-boot.bat stop
```



### Logging ###

설치된 Agent Server가 실행되면 설치 위치의 하위에 `logs` 디렉토리가 생성되고 `transfer-file.log` 로그 파일과 프로세스 제어를 위해 필요한 `.lock`, `.pid`파일이 생성됩니다.  생성되는 로그 파일의 위치와 로깅 레벨은 `config/transfer--ile.yml`파일을 수정하여  변경할 수 있습니다.



1. Command Line Control 

배포된 패키지에는 Command Line으로 Agent Server를 운영할 수 있도록 Windows 환경에 필요한 `agent-boot.bat` 파일이 포함되어 있다.  

 * Agent Server를 시작하기 위해서는 Windows에서 제공하는 Command Prompt (CMD)를 이용하여 설치 위치로 이동 후 아래 Command를 실행한다.
``` bash
agent-boot.bat start
``` 
 * 다음 Command를 이용하여 실행 중인 Agent Server의 상태를 확인할 수 있다.
``` bash
agent-boot.bat status
```
 * 실행 중인 Agent Server는 다음 Command를 이용하여 중지한다.
``` bash
agent-boot.bat stop
```

2. Windows System Service

배포된 패키지는 Windows System Service로 Agent Server를 등록하여 운영할 수 있도록 시스템 서비스 등록과 테스트, 등록 해제에 사용되는 아래 파일들을 포함한다.

 * bxi-file-agent.NET2.exe
 * bxi-file-agent.NET4.exe
 * bxi-file-agent.service.exe
 * bxi-file-agent.service.xml

- - - 
 
 1. install
 
실행 파일을 이용하여 Agent Server를 Windows System Service로 등록한다.

``` bash
bxi-file-agent.service.exe install
```
 
목표 시스템이 .NET4를 지원하지 않는 경우 포함되어 있는 `bxi-file-agent.NET2.exe`파일을 이용한다. 
이 때에는 반드시 파일의 이름을 `bxi-file-agent.service.exe`로 변경하여야 한다.
서비스 등록이 정상적으로 완료되면 `제어판->관리 도구->서비스`에서 정상 등록 여부룰 확인할 수 있다.

아래는 서비스 등록에 사용되는 설정파일 `bxi-file-agent.service.xml`의 내용이다.

``` xml
<service>
    <id>Bxi-file-agent</id>
    <name>Bxi-file-agent</name>
    <description>This service runs bxi file agent.</description>
    <env name="AGENT_HOME" value="%BASE%"/>
    <executable>java</executable>
    <arguments>-XX:+UseG1GC -Xms1024m -Xmx4096m -jar "%BASE%\lib\bxi-file-agent.jar" --spring.config.location=%BASE%\config\application.yml --context.location=file:%BASE%\config --logging.file=%BASE%\logs\bxi-file-agent.log</arguments>
    <workingdirectory>%BASE%</workingdirectory>
    <stoptimeout>5 sec</stoptimeout>
    <startmode>Automatic</startmode>
    <delayedAutoStart/>
    <logpath>%BASE%\logs</logpath>
    <logmode>rotate</logmode>
</service>
```

설정 파일에 `startMode`가 `Automatic`으로 기본 설정되어 있고 `<delayedAutoStart/>`항목이 지정되어 있으므로 Agent Server Service는 Window System 시작과 함께 `지연시작` 된다.
`startMode`는 Boot, System, Automatic, Manual로 변경 지정될 수 있다. 


 2. uninstall
 
실행 파일을 이용하여 등록되어 있는 Agent Server를 제거할 수 있다.

``` bash
bxi-file-agent.service.exe uninstall
```
  
 3. start / stop / restart
  
Agent Server가 Windows System Service로 등록된 후에는 `startMode` 지정에 따라 시작 조건에 따라 시작된다. 
정상 동작 여부를 확인하기 위해 테스트가 필요한 경우 아래처럼 Service를 시작하거나 중단할 수 있다.

``` bash
bxi-file-agent.service.exe start

bxi-file-agent.service.exe stop

bxi-file-agent.service.exe restart
``` 

 4. status
  
아래 명령을 이용하여 Agent Server의 서비스 등록 여부와 실행 상태를 확인할 수 있다.

``` bash
bxi-file-agent.service.exe status
``` 
  * nonExistent: 서비스로 등록되어 있지 않음
  * Started: 서비스가 시작됨
  * Stopped: 등록된 서비스가 중단되었거나 실행 중이 아님

### Unix/Linux Operating System

1. Command Line Control 

배포된 패키지에는 Command Line으로 Agent Server를 운영할 수 있도록 Unix/Linux 환경에 필요한 `agent-boot.sh` 파일이 포함되어 있다.  

 * Agent Server를 시작하기 위해서는 설치 위치로 이동 후 아래 Command를 실행한다.
``` bash
agent-boot.sh start
``` 
 * 다음 Command를 이용하여 실행 중인 Agent Server의 상태를 확인할 수 있다.
``` bash
agent-boot.sh status
```
 * 실행 중인 Agent Server는 다음 Command를 이용하여 중지한다.
``` bash
agent-boot.sh stop
```

2. Installation as an init.d Service (System V)

배포된 패키지의 `lib/bxi-file-agent.jar` 파일은 fully executable jar 형태로 빌드되어 있으므로 symbolic link를 생성하여 System Service로 등록되고 실행될 수 있다.
서비스 등록을 위해 root 권한으로 아래처럼 symbolic link를 생성한다.

``` bash
sudo ln -s ${Replace with actual installation location}/lib/bxi-file-agent.jar /etc/init.d/bxi-file-agent 
```  

서비스 등록 후에는 아래 처럼 Agent Server를 제어할 수 있다.

``` bash
service bxi-file-agent {start|stop|force-stop|restart|force-reload|status|run}
```

### Environment variables

Agent Server 패키지는 다양한 실행 환경을 제공함에 따라 JVM에 환경변수를 전달하는 여러 종류의 설정 파일이 사용된다. 
그러나 JVM이 시작된 이후 Agent Server에 적용되는 모든 설정 항목은 `config/application.yml`에 포함된다.

아래는 실행 방법에 따라 maximum heap size 등을 수정하거나 JVM 실행 옵션을 추가해야 하는 경우 변경할 항목들을 나열한다.

 * Windows Command Line: JAVA_OPTS variable in agent-boot.bat
 * Windows System Service: arguments element in bxi-file-agent.service.xml, reinstall required 
 * Unix/Linux Command Line: JAVA_OPTS variable in agent-boot.bat  
 * Unix/Linux System Service: JAVA_OPTS variable in bxi-file-agent.conf, reinstall not required

- - -  