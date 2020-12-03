#!/bin/bash
#
# Startup script for a spring boot project
#
# description: Transfer File Agent

# Source function library.
[ -f "/etc/rc.d/init.d/functions" ] && . /etc/rc.d/init.d/functions
[ -z "$JAVA_HOME" -a -x /etc/profile.d/java.sh ] && . /etc/profile.d/java.sh


# the name of the project, will also be used for the war file, log file, ...
APP_NAME=transfer-file

# the agent boot jar-file
APP_JAR="lib/transfer-file.jar"
LOG_FILE="logs/transfer-file.log"

# java executable for spring boot app, change if you have multiple jdks installed
JAVA_EXEC="$JAVA_HOME/bin/java"
JAVA_OPTS="--spring.config.location=file:./config/transfer-file.yml --logging.file=$LOG_FILE --spring.jmx.enabled=true -XX:+UseG1GC -Xms1024m -Xmx2048m"
LOCK="logs/.lock"
PID="logs/.pid"

RETVAL=0

pid_of_boot() {
    #pgrep -f "java.*$APP_NAME"
    cat $PID
}

start() {

    [ -e "$LOG_FILE" ] && cnt=`wc -l "$LOG_FILE" | awk '{ print $1 }'` || cnt=1

    echo -n $"Starting $APP_NAME: "
    
    nohup $JAVA_EXEC -jar $APP_JAR $JAVA_OPTS >>/dev/null 2>&1 &

    sleep 5
    while ! { grep -m1 'Started TransferServer' < $LOG_FILE ; }; do
        sleep 1
    done
    
    echo $! > $PID 

    #pid_of_boot > /dev/null
    
    RETVAL=$?
    [ $RETVAL = 0 ] && success $"$STRING" || failure $"$STRING"
    echo

    [ $RETVAL = 0 ] && touch "$LOCK"
}

stop() {
    echo -n "Stopping $APP_NAME: "

    pid=`pid_of_boot`
    [ -n "$pid" ] && kill $pid
    RETVAL=$?
    cnt=10
    while [ $RETVAL = 0 -a $cnt -gt 0 ] &&
        { pid_of_boot > /dev/null ; } ; do
            sleep 1
            ((cnt--))
    done

	[ $RETVAL = 0 ] && rm -f "$PID"
    [ $RETVAL = 0 ] && rm -f "$LOCK"
    #[ $RETVAL = 0 ] && success $"$STRING" || failure $"$STRING"
    [ $RETVAL = 0 ] && echo $"$STRING" || echo $"$STRING"
    echo
}

status() {
    pid=`pid_of_boot`
    if [ -n "$pid" ]; then
        echo "$APP_NAME (pid $pid) is running..."
        return 0
    fi
    if [ -f "$LOCK" ]; then
        echo "$APP_NAME dead but agent locked"
        return 2
    fi
    echo "$APP_NAME is stopped"
    return 3
}

# See how we were called.
case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    restart)
        stop
        start
        ;;
    *)
        echo $"Usage: $0 {start|stop|restart|status}"
        exit 1
esac

exit $RETVAL