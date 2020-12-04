# Transfer FIle Agent Quick Test #
## Client to Server Resources ##

제공된  라이브러리를 이용하여 작성된 클라이언트에서 파일을 전송하고 요청하여 수신하거나 파일 정보를 요청하는 내용을 확인합니다. 라이브러리는 별도의 단위 시스템에 적용되어 Transfer File Agent Server로의 요청을 작성하는 데 사용됩니다.
### PUT Resource ###

단위 시스템 또는 사용자 환경에서 파일을 전송합니다.

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain put localhost 8024 ./parallel-content.zip
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain put localhost 8024 ./parallel-content.zip
```
### PUT Parallel Resource ###

단위 시스템 또는 사용자 환경에서 대용량 파일을 병렬처리하여 전송합니다.

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain putParallel localhost 8024 ./parallel-content.zip
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain putParallel localhost 8024 ./parallel-content.zip
```
### GET Resource ###

Transfer File Agent에 요청하여 단위 시스템 또는 사용자 환경으로 파일을 수신합니다.

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain get localhost 8024
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain get localhost 8024
```
### EXIST Resource ###

Transfer File Agent에 요청을 전송하여 Transfer File Agent 리파지토리에 파일이 있는지 확인합니다.

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain exist localhost 8024
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain exist localhost 8024
```
## Server to Server Resources ##

Transfer File Agent간의 파일 전송 명령을 전달하여 Agent Server간에 파일을 전송합니다.
### TRANSFER Resource ###

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain transfer localhost 8024 localhost 8025
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain transfer localhost 8024 localhost 8025
```
## Management ##

Transfer File Agent에는 Server의 정상 여부를 확인하기 위한 서비스와 운영 정보를 제공하는 서비스를 포함하고 있습니다.
### Health Check ###

Transfer File Agent에 Server의 정상 동작 여부를 요청하여 확인합니다.

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain health localhost 8024
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain health localhost 8024
```
### Information ###

Transfer File Agent에는 Server의 운영 정보를 요청하여 확인합니다.있습니다.

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain info localhost 8024
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain info localhost 8024
```
### Shutdown endpoint ###

- Linux
```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain shutdown localhost 8024
```
- Windows
```
java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain shutdown localhost 8024
```