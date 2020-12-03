# TEST ME #

## Client to Server Resources ##

### PUT Resource ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain put localhost 8024 ./parallel-content.zip

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain put localhost 8024 ./parallel-content.zip

```

### PUT Parallel Resource ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain putParallel localhost 8024 ./parallel-content.zip

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain putParallel localhost 8024 ./parallel-content.zip
```

### GET Resource ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain get localhost 8024

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain get localhost 8024
```

### EXIST Resource ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain exist localhost 8024

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain exist localhost 8024
```

## Server to Server Resources ##

### TRANSFER Resource ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain transfer localhost 8024 localhost 8025

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain transfer localhost 8024 localhost 8025
```


## Management ##

### Health Check ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain health localhost 8024

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain health localhost 8024

```

### Information ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain info localhost 8024

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain info localhost 8024

```

### Shutdown endpoint ###

```bash
java -cp "./lib/*:./lib" easymaster.transfer.file.client.QuicktimeTestMain shutdown localhost 8024

java -cp "./lib/*;./lib" easymaster.transfer.file.client.QuicktimeTestMain shutdown localhost 8024

```