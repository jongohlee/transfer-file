# BXI File Agent Developer Information

문서에서는 BXI File Agent Server에서 정의하여 사용되는 Protocol과 제공하는 세부 커맨드를 설명하고 있으며, 
사이트 적용 과정에서 커스터마이징이 필요한 경우 참고할 수 있는 정보를 제공한다.

에이전트 서버 운영을 위해 필요한 정보는 다음 문서를 참고한다.

**Operating documentation: [User Guide](README.md)**


## Frame Message Protocol

Agent Server의 요청과 응답 메시지는 다음과 같이 구성된다.

 **Protocol Layout**
  
| Part   | Elements | Description |
|:----|:-----------------------------------------|:-----------------------------------------|
| COMMAND  | {GET\|PUT\|INFO\|ACTION\|TRANSFER} /uri   | 요청과 응답에 명시적으로 전달할 Command와 세부 명령을 표현할 Uri |
| DEFINED HEADER  | Agent<br>Remote<br>Agent-Type<br>Response-Code<br>Reason<br>Connection<br>Content-Length<br>Deleted-Count<br>Transfer-Encoding<br>Transfer-Source-Uri<br>Transfer-Destination-Uri<br>Destination-Agent<br>Transfer-Validation<br>Transfer-Timeout-Seconds<br>Transfer-Before-Script<br>Transfer-After-Script<br>Transfer-Interceptor<br>Merge-Resource<br>Session-Id<br>Resource-Length<br>Transferred-Resource    | 요청처리와 응답에 필요한 정보를 위해 미리 정의되어 있는 헤더 항목들 |
| CUSTOM HEADER   | ex) Request-AgentUser    | 커스터마이징 되는 선후처리기에 전달되어 사용할 사용자 정의 헤더 항목들로 임의의 이름과 값으로 추가될 수 있음 |
| CONTENT  | bytes contents<br>...<br>      | 전송/수신 Contents |

- - -

### COMMANDS

 * INFO /health  : Agent Server에 Health 정보를 요청
 * INFO /info  : Agent Server에 시스템 정보를 요청
 * INFO /exist  : Agent Server에 특정 리소스가 존재하는지 확인 요청
 * ACTION /session : 하나의 트랜잭션(ex) 대형 리소스 분할 전송)이 순차 또는 병렬로 교환되는 여러 요청과 응답으로 처리되어야 하는 경우 완전한 처리를 보장하기 위해 사용될 session의 생성을 Agent Server에 요청
 * ACTION /merge : 병렬로 분할 전송된 대형 리소스의 병합 요청
 * ACTION /shutdown : Agent Server Shutdown 요청
 * GET : Agent Server에 특정 리소스의 전송을 요청
 * PUT : Agent Server에 특정 리소스를 전송
 * LIST : Agent Server의 특정 위치에 존재하는 리소스<b>들</b>의 목록을 요청
 * DELETE : Agent Server의 특정 위치에 존재하는 리소스<b>들</b>의 삭제를 요청
 * TRANSFER : Agent Server의 특정 리소스<b>들</b>을 다른 Agent Server Instance에 전송하도록 요청 

- - - 
 
### DEFINED HEADERS

다음에서는 사용자에 의해 지정되거나 이해해야할 필요가 있는 주요 헤더 항목을 설명한다.

| Header Element &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;  | Description | Examples |
|:---------------------|:---------------------|:---------------------------|
| Response-Code  | 응답에만 포함되며 요청에 대한 처리 결과 | 200= OK<br>210= Not Exist<br>400= Bad Request<br>402= Bad Response<br>404= Source File Not Found<br>405= Already Exist<br>500= Internal Server Error<br>510= Destination Not Responding<br>520= Transfer Failed<br>525= Delete Failed<br>530= File Permission Error<br>540= TimeOut Occurred |
| Reason  | 오류에 대한 결과 또는 정상 처리시 일부 정보를 포함 | Response-Code: 520 Transfer Failed<br>Reason: agent: [192.168.12.15:8024, 192.168.12.16:8024] are not responding |
| Content-Length  | 전달되는 CONTENT영역의 크기 | Content-Length: 247549 |
| Transfer-Encoding  | Chunk 단위 전송 여부 | Transfer-Encoding: chunked |
| Content-Length  | 전달되는 CONTENT영역의 크기 | Content-Length: 247549 |
| Resource-Length  | EXIST command 요청의 결과로 리소스가 존재하는 경우 해당 리소스의 길이 정보 | Reason: EXIST<br>Resource-Length: 557 |
| Deleted-Count  | DELETE command 요청에 따른 처리 결과 | Deleted-Count: 2<br>Reason: /agent/backup/20180717/check1.done<br>Reason: /agent/backup/20180717/check2.done |
| Transferred-Resource  | TRANSFER command 요청에 따라 source agent에서 target agent로 전송된 결과 | Reason: 2 files are transferred, 0 files are failed<br>Transferred-Resource: /agent/2432345.dat<br>Transferred-Resource: /agent/32453.dat |
| Transfer-Source-Uri  | 리소스를 전송하거나 전송을 요청할 때 대상이 되는 리소스와 파마리터로 구성된 URI | Transfer-Source-Uri: agent://192.168.12.15:8025/fixed-content.txt?interceptor=simpleCustomTransferInterceptor&afterTransfer=backup |
| Transfer-Destination-Uri  | 리소스를 전송하거나 전송을 요청할 때 수신위치에서 리소스를 저장할 위치와 파마리터로 구성된 URI | Transfer-Destination-Uri: /backup/${date:now:yyyyMMdd}/${file:name.noext}.bak?interceptor=simpleCustomReceiveInterceptor&createAck=.done&onExist=overwriteOnExist |
| Transfer-Interceptor<br>Transfer-Before-Script<br>Transfer-After-Script  | TRANSFER command와 함께 전달되는 선후처리 항목으로 처리를 담당할 bean 등록 이름을 interceptor에 지정하거나 python script의 위치를 script항목에 지정한다. URI에 Parameter로 포함된 선후처리기는 송수신 Agent위치에서 송신과 수신 선후에 실행되며 헤더 항목에 포함된 선후처리 지정은 TRANSFER command 처리 선후에 실행된다. | Transfer-Before-Script: replace.py arg1 arg2<br>Transfer-Interceptor: simpleCustomTransferInterceptor |
| Connection  | 요청측에 Connection을 close해야 하는지 여부로 closing은 자동으로 처리됨 | Response-Code: 520<br>Transfer Failed<br>Reason: agent: [192.168.12.15:8024, 192.168.12.16:8024] are not responding<br>Connection: close |

- - - 

### URI(Uniform Resource Identifier)를 이용한 경로와 리소스 지정

**URI 구성**

경로와 리소스를 지정하기 위해 Header 항목(Transfer-Source-Uri, Transfer-Destination-Uri...)에 URI를 지정한다.
URI에는 전송 선후 처리(backup, delete, interceptor 실행)등의 옵션을 지정하기 위한 parameter를 포함한다.

`ex) agent://192.168.219.141:8024/9063C6480000.dat?&createAck=.done&onExist=overwriteOnExist`

리소스의 목록 요청과 삭제 명령에 사용되는 URI에는 ant style pattern 문자열이 지정될 수 있다.

`ex) agent://192.168.219.141:8024/backup/**/*`

경로 URI에는 다음 항목들이 파라미터로 포함될 수 있다. 

| Name | Description | Examples |
|:---------------------|:---------------------|:---------------------------|
| site  | 업무별로 site를 분리하여 운영 중인 경우 처리할 리소스의 site Id | site=accountMgmt |
| createAck  | 리소스를 수신하는 Agent에서 수신 완료 파일 생성 여부와 확장자 | createAck=.done |
| afterTransfer  | 리소스 송신 Agent에서 송신 완료 후의 처리 | afterTransfer=backup<br>afterTransfer=delete |
| beforeScript  | command 처리 전에 실행될 python script | beforeScript=update.py arg1 arg2 arg3 |
| afterScript  | command 처리 후에 실행될 python script  | afterScript=clear.py arg1 arg2 arg3 |
| interceptor  | command 처리 선후에 실행될 선후처리기 Bean 등록 이름 | interceptor=simpleCustomReceiveInterceptor |
| onExist  | 전송 대상 파일이 수신 Agent에 존재하는 경우 처리 방법 | onExist=overwriteOnExist<br>onExist=appendOnExist<br>onExist=failOnExist<br> |

실제 어플리케이션 수준에서 URI는 사용자에 의해 직접 생성되지 않고 다음 API를 이용하여 인코딩되어 생성된다.
 
``` java
String uri= AgentMessageUtil.encodedUri( "192.168.42.24", 8025, "d324234.dat",
                            OptionParam.param( OptionParam.INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParam.param( OptionParam.INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParam.param( OptionParam.SITE, "accountMgmt"));
```


**PATH Expression**

경로 URI를 생성할 때는 다이나믹하게 생성되어야 하는 일부 문자열을 아래의 예처럼 expression을 이용하여 지정할 수 있다.

example)
```
agent://192.168.219.141:8024/${property[agent.repository.sites.accountMgmt.backup-dir]}/${date:now-48h:yyyyMMdd}/${file:name.noext}.bak

evaluated : agent://192.168.219.141:8024/agent/accounts/backup/20180718/d3246.bak
..

agent://192.168.219.141:8024/${ref:staticCustomEvaluator}/${date:now-48h:yyyyMMdd}/${file:name.noext}.bak

evaluated : agent://192.168.219.141:8024/agent/custom_biz/toTransfer/20180718/d3246.bak

```

expression 처리 결과로 대체되어야 할 부분은 `${`로 시작하여 `}`로 완료된다.

Agent Server에서는 다음의 expression function을 제공한다.

 * property : 시스템 환경변수, JVM에 전달된 실행 인수, application.yml에 설정된 모든 항목 값으로 대체
 * date : ${date:command:pattern}의 형태로 사용된다. 
   * command : 현재 시간을 가져오는 now와 이에 연결되는 연산자를 지원한다. ex) now-48h+60m-100s
   * pattern : 문자열로 변활할 때 사용할 패턴 문자열을 지정한다.
 * file : 파일명을 기준으로 대체할 문자열을 생성한다. 처리할 원본의 파일 명이 존재해야 하므로 수신 URI에만 지정할 수 있다.
   * file:name : 경로를 제거한 파일 명
   * file:name.noext : 경로와 확장자를 제거한 파일 명
   * file:ext | file:name.ext : 파일의 확장자
   * file:parent | file:path : 파일 경로
   * file:absolute.path : 파일의 절대 경로로 parent, path와 같은 값으로 변환된다. 
   * file:length | file.size : 파일의 크기
   * file:modified : 파일의 lastModified long value
 * ref : `config/custom-context.xml`에 등록되어 있는 bxi.agent.file.language.simple.Expression type의 bean을 호출한 결과의 문자 표현으로 변환된다. bean 호출시 전달되는 ExpressionContext isntance에는 Spring ApplicationContext instance와 Request Header에 전달된 모든 항목 그리고 현재 처리 중인 파일이 있는 경우 파일의 절대 경로 정보가 포함되어 있다.
 
다음은 참고용으로 작성되어 배포 패키지에 포함되어 있는 custom evaluator 구현이다.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="staticCustomEvaluator" class="bxi.agent.file.sample.StaticCustomEvaluator">
        <property name="staticValue" value="custom"/>
    </bean>
    
</beans>
```  

``` java
package bxi.agent.file.sample;

import java.util.List;

import bxi.agent.file.language.simple.Expression;
import bxi.agent.file.language.simple.ExpressionContext;
import bxi.agent.file.protocol.AgentHeaderNames;

public class StaticCustomEvaluator implements Expression
{
    private String staticValue;

    @SuppressWarnings( "unchecked")
    @Override
    public <T> T evaluate( ExpressionContext context, Class<T> type)
    {
        Object another= context.getApplicationContext().getBean( "another");
        String path= context.getCurrentHandlingFilePath();
        String contentLength= context.getHeader( AgentHeaderNames.CONTENT_LENGTH);
        List<String> scripts= context.getHeaders().getAll( AgentHeaderNames.TRANSFER_AFTER_SCRIPT);
 
        return (T)staticValue;
    }

    /**
     * @return the staticValue
     */
    public String getStaticValue()
    {
        return staticValue;
    }

    /**
     * @param staticValue the staticValue to set
     */
    public void setStaticValue( String staticValue)
    {
        this.staticValue= staticValue;
    }

}

```

- - -  
 
### Agent Client

실제 배포되어 있는 Agent Server를 사용하는 어플리케이션에서는 위에 설명된 헤더 항목들을 모두 명시적으로 지정하여 제공되는 기능을 사용할 필요는 없으며, 보다 원활히 기능을 사용할 수 있도록 Client API Set을 제공한다.

클라이언트 배포 패키지는 `dist/bxi-file-agent-1.0.1-client.zip`에서 제공되며 실행을 위해 필요한 의존 라이브러리를 포함하고 있다. 

다음은 제공되는 주요 API에 대한 설명과 사용 예이다.

**simple example**

``` java

    // client 생성 후 연결을 생성하고 연결을 재사용하는 경우

    AgentClient client= AgentClient.create( "192.168.219.141", 8024);
    Channel channel= null;

    try
    {
        channel= client.connect();
        
        Health health= client.requestHealth( channel);
        if( Status.UP.equals( health.getStatus()))
        {
            boolean answer= client.requestResourceExist( channel,
                    "/${date:now:yyyyMMdd}/parallel-content.jar", null);
            logger.info( "result: {}", answer);
        }
    }
    catch( Exception e)
    {
        logger.error( e.getMessage(), e);
    }
    finally
    {
        if( channel!= null) channel.close();
        client.shutdown();
    }

    // agentClient가 필요한 연결을 매번 생성하여 사용하도록 위임하는 경우  

    AgentClient client= AgentClient.create( "192.168.219.141", 8024);
    try
    {
        boolean answer= client.requestResourceExist( AgentClient.THROWAWAY,
                "/${date:now:yyyyMMdd}/parallel-content.jar", null);
        logger.info( "result: {}", answer);
    }
    catch( Exception e)
    {
        logger.error( e.getMessage(), e);
    }
    finally
    {
        client.shutdown();
    }

```

**Agent Client APIs**

``` java

    // 1. Agent Server의 실행환경 정보를 요청
    public Map<String, String> requestServerInfo( Channel channel) throws Exception
    
    public Map<String, String> requestServerInfo( Channel channel, long timeout) throws Exception

    // 2. Agent Server의 Health 상태를 확인
    public Health requestHealth( Channel channel) throws Exception
    
    public Health requestHealth( Channel channel, long timeout) throws Exception

    // 3. 특정 리소스가 Agent Server의 특정 위치에 존재하는지 확인
    public boolean requestResourceExist( Channel channel, String path, String site) throws Exception
    
    public boolean requestResourceExist( Channel channel, String path, String site, long timeout) throws Exception

    // 4. Agent Server로부터 특정 리소스를 요청하여 응답을 처리
    //    Functional Interface를 이용하여 응답 컨텐츠에 대한 처리를 작성한다.
    public <R> R requestGetResource( Channel channel, String path, String site,
            Function<AgentFileData, R> operator, OptionParam... options) throws Exception

    public <R> R requestGetResource( Channel channel, String path, String site,
            Function<AgentFileData, R> operator, long timeout, OptionParam... options) throws Exception

    // 아래는 단순한 사용 예이다.
    AgentClient client= AgentClient.create( "192.168.219.141", 8024);

    try
    {
        boolean answer= client.requestGetResource( AgentClient.THROWAWAY, "/${date:now:yyyyMMdd}/parallel-content.jar", 
            null, content->{ 
                try
                {
                    content.renameTo( new File( "/DATA/easymaster/Workings/systemd-agent/bxi-file-agent-1.0.1/get.jar"));
                    return true;
                }
                catch( IOException e)
                {
                    return false;
                }
            }, new OptionParam[] {});
          logger.info( "result: {}", answer);
      }
      catch( Exception e)
      {
          logger.error( e.getMessage(), e);
      }
      finally
      {
          client.shutdown();
      }

    // 5. 특정 리소스를 Agent Server에서 삭제한다.
    //    처리 결과로 정상적으로 삭제된 리소스목록을 가져온다.
    public List<String> requestDeleteResources( Channel channel, String path, String site, OptionParam... options)
            throws Exception

    public List<String> requestDeleteResources( Channel channel, String path, String site,
            long timeout, OptionParam... options) throws Exception
            
    // 6. 경로와 패턴 문자열을 이용하여 Agent Server로부터 리소스의 목록을 가져온다.
    public List<String> requestListResources( Channel channel, String path, String site, OptionParam... options)
            throws Exception

    public List<String> requestListResources( Channel channel, String path, String site,
            long timeout, OptionParam... options) throws Exception

    // 7. Agent Server에 특정 파일을 전송하여 저장하도록 한다.            
    public boolean requestPutResource( Channel channel,
            File resource, String path, String site, OptionParam... options) throws Exception

    public boolean requestPutResource( Channel channel, File resource, String path, String site,
            long timeout, OptionParam... options) throws Exception

    // 8. 대형 파일을 병렬로 처리하여 Agent Server에 전송한다.
    public boolean requestPutParallelResource( File resource, String path, String site, OptionParam... options)
            throws Exception
    
    // 병렬 처리로 파일을 전송할 때는 동시 처리를 위해 다음처럼 AgentClient 생성시에 적절한 worker count를 지정해주어야 한다.
    AgentClient client= AgentClient.create( "192.168.219.141", 8028, 10);
    try
    {
        File resource= ResourceUtils.getFile( "./src/test/resources/parallel-content.jar");
        boolean answer= client.requestPutParallelResource( resource,
            "/backup/${date:now:yyyyMMdd}/${file:name.noext}.bak", "biz1",
            OptionParam.param( OptionParam.ON_EXIST, OptionParamValues.OVERWRITE_ONEXIST),
            OptionParam.param( OptionParam.CREATE_ACK, ".ack"),
            OptionParam.param( OptionParam.INTERCEPTOR, "simpleCustomReceiveInterceptor"));

        logger.info( "result: {}", answer);
    }
    catch( Exception e)
    {
        logger.error( e.getMessage(), e);
    }
    finally
    {
        client.shutdown();
    } 

    // 9. 특정 Agent Server에 Transfer 요청을 전송하여 다른 타겟 Agent Server에 리소스를 전송하도록 한다.
    public boolean requestTransferResources( Channel channel, List<TransferRequest> trans,
            boolean sync, OptionParam... options) throws Exception
    
    public boolean requestTransferResources( Channel channel, List<TransferRequest> trans,
            boolean sync, long timeout, OptionParam... options) throws Exception
            
    AgentClient client= AgentClient.create( "192.168.219.141", 8028); // 송신 Agent Server에 연결 

    try
    {
        List<TransferRequest> trans= new ArrayList<TransferRequest>(); // 전송 대상을 여러 개 지정할 수 있다.
        trans.add( new TransferRequest()
            .from( client)
            .resource( "/backup/${date:now:yyyyMMdd}/parallel-content.large", OptionParam.param( OptionParam.SITE, "biz1"))
            .to( "192.168.219.141", 8024)
            .to( "192.168.219.142", 8024) // 수신 Agent Server가 여러 개 지정된 경우 첫번 째 Agent Server가 응답하지 않는 경우 두번 째 Agent Server에 전송을 시도한다.
            .path( "${date:now:yyyyMMdd}/${file:name.noext}.jar",
                OptionParam.param( OptionParam.INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParam.param( OptionParam.CREATE_ACK, ".done"),
                OptionParam.param( OptionParam.ON_EXIST, OptionParamValues.OVERWRITE_ONEXIST)));

        boolean answer= client.requestTransferResources( AgentClient.THROWAWAY, trans, true, -1,
                    OptionParam.param( OptionParam.INTERCEPTOR, "simpleCustomTransferInterceptor"));
        logger.info( "result: {}", answer);
    }
    catch( Exception e)
    {
        logger.error( e.getMessage(), e);
    }
    finally
    {
        client.shutdown();
    }
    
    // 10. 원격 Agent Server를 shutdown 시킨다.
    public boolean requestShutdownCommand( Channel channel) throws Exception

    // 11. 범용적으로 사용할 수 있도록 ResponseComsumer Functional Interface를 인자로 받는 API
    // ResponseConsumer Function에서 응답으로 전달된 AgentMessage를 처리할 수 있다.  
    public <R> R request( Channel channel, AgentMessage request, ResponseConsumer<AgentMessage, R> consumer)
        throws RequestHandlerException, ResponseHandlerException

    public <R> R request( final Channel channel, AgentMessage request, ResponseConsumer<AgentMessage, R> consumer,
            long timeout) throws RequestHandlerException, ResponseHandlerException
            
```

- - -


### User Defined Beans

**System Interceptors**
Agent Server에는 Server Instance별로 설정되어 모든 요청의 선후에 반드시 실행되는 선후 처리 Interceptor를 지정할 수 있다. 

다음은 참고용으로 작성되어 배포 패키지에 포함되어 있는 custom system interceptor 구현이다.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="agentInterceptor" class="bxi.agent.file.sample.SimpleAgentInterceptor"/>
    
</beans>
```  

``` java

package bxi.agent.file.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bxi.agent.file.processor.AgentInterceptor;
import bxi.agent.file.processor.TransferContext;

public class SimpleAgentInterceptor implements AgentInterceptor
{
    private Logger logger= LoggerFactory.getLogger( SimpleAgentInterceptor.class);

    @Override
    public boolean preProcess( TransferContext context) throws Exception
    {
        logger.debug( "{} interceptor preProcess ........................... executed", getClass());
        return true;
    }

    @Override
    public void postProcess( TransferContext context) throws Exception
    {
        logger.debug( "{} interceptor postProcess ........................... executed", getClass());
    }

    @Override
    public void afterCompletion( TransferContext context) throws Exception
    {
        logger.debug( "{} interceptor afterCompletion ........................... executed", getClass());
    }
}

```

**Request Interceptors**

또한 요청별로 헤더(Transfer-Interceptor: TRANSFER command시 요청 처리 선후에 실행)에 지정되거나 URI Parameter(interceptor=simpleCustomTransferInterceptor, 송신 URI에 지정된 경우 송신 전후에 실행되고 수신 URI에 지정된 경우 수신 전후에 실행됨)에 하나 이상 지정되어 실행된다.

bean 호출시 전달되는 TransferContext isntance에는 Spring ApplicationContext instance와 Request Header에 전달된 모든 항목 그리고 현재 처리 중인 파일이 있는 경우 파일의 절대 경로 정보들이 포함되어 있다.

다음은 참고용으로 작성되어 배포 패키지에 포함되어 있는 custom request interceptor 구현이다.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- 송신 전후에 실행되는 Interceptor -->
    <bean id="simpleCustomTransferInterceptor" class="bxi.agent.file.sample.SimpleCustomTransferInterceptor"/>
    
    <!-- 수신 전후에 실행되는 Interceptor -->
    <bean id="simpleCustomReceiveInterceptor" class="bxi.agent.file.sample.SimpleCustomReceiveInterceptor"/>
    
</beans>
```  

``` java

package bxi.agent.file.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bxi.agent.file.processor.TransferContext;
import bxi.agent.file.processor.TransferInterceptor;

public class SimpleCustomTransferInterceptor implements TransferInterceptor
{
    private Logger logger= LoggerFactory.getLogger( SimpleCustomTransferInterceptor.class);

    @Override
    public boolean preTransfer( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} preTransfer", SimpleCustomTransferInterceptor.class);
        // check if required
        return true;
    }

    @Override
    public void postTransfer( TransferContext context, Exception cause) throws Exception
    {
        logger.debug( "custom interceptoer {} postTransfer", SimpleCustomTransferInterceptor.class);
        // process if required
    }

    @Override
    public void afterCompletion( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} afterCompletion", SimpleCustomTransferInterceptor.class);
        // process if required
    }
}


package bxi.agent.file.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bxi.agent.file.processor.ReceiveInterceptor;
import bxi.agent.file.processor.TransferContext;

public class SimpleCustomReceiveInterceptor implements ReceiveInterceptor
{
    private Logger logger= LoggerFactory.getLogger( SimpleCustomReceiveInterceptor.class);

    @Override
    public boolean preReceive( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} preReceive", SimpleCustomReceiveInterceptor.class);
        // check if required
        return true;
    }

    @Override
    public void postReceive( TransferContext context, Exception cause) throws Exception
    {
        logger.debug( "custom interceptoer {} postReceive", SimpleCustomReceiveInterceptor.class);
        // process if required
    }

    @Override
    public void afterCompletion( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} afterCompletion", SimpleCustomReceiveInterceptor.class);
        // process if required
    }

}


```

**Request Script**
Agent Server의 실행환경에서 python으로 작성되어 있는 파일 처리 선후 처리를 재사용해야 하거나 script 적용이 선호되는 경우 이를 사용할 수 있도록 Agent Server에서는 요청의 선후에 실행될 Script가 지정된 경우 이를 실행한다.

다음은 테스트환경에서 선후처리에 사용된 script 예이다.
Spring bean형태로 작성된 interceptor를 호출할 때와 마찬가지로 처리 중인 Request의 헤더 항목과 설정(application.yml)에 지정된 항목 값, 현재 처리 중인 파일의 경로 값들에 접근할 수 있다.


``` python

#!/usr/bin/python2.7

###
# Forwarded read-only attributes from agent
# AgentHeaders attributes
# Current-File
# Environment Properties
###

import sys

class SayHello:
    
    
    def sayhello(self):
        print '-------------------------------------------------------------------------'
        print 'Number of arguments:', len(sys.argv), 'arguments.'
        print 'Argument List:', str(sys.argv)
        print 'Agent : '+ headers['Agent']
        if( len(currentFiles) > 0):
            print 'Current_File : '+ currentFiles[0]
        print 'agent.repository.base-dir : '+ environment['agent.repository.base-dir']
        print '-------------------------------------------------------------------------'
        print '-------------------------------------------------------------------------'

    def __init__(self, context, environment, headers):
        if(context != None):
            print 'invoke from agent - not command line'

def main():
    SayHello(context, environment, headers).sayhello()

if __name__ == '__builtin__':
    sys.argv = commandlineArgs
    main()
elif __name__ == '__main__':
    context = None
    environment = None
    headers = None
    main()


```
