#!/bin/bash
###
#   george.li 2013/1/9
#
#   simple daemon template
#
#   env var
#       DAEMON: daemon program path
#       NAME:   suggest daemon name
#       PID_FILE: daemon pid file path
#
#   command
#       start: start daemon
#       stop:  stop  daemon
#       restart: restart daemon
#       status: try daemon is on use
#
###

# set your daemon program
DAEMON=/usr/bin/radar_sim_server
# set you daemon default args
DAEMON_OPTS=
# set daemon name
NAME=radar_sim_server
#---------------------------



PID_FILE=/tmp/$NAME.pid
DESC="simple daemon template"

test -x $DAEMON || exit 0

#scan pid is on line
touch $PID_FILE

###
#try pid is running: then set STATUS=1
###
function status_test() {
    PID=`cat $PID_FILE 2>/dev/null||echo 0`

    STATUS=0
    ps aux|awk -v pid=$PID '$2==pid{print}'|grep "$DAEMON" >/dev/null&&STATUS=1
}


###
#   start
###
function start_daemon() {
        status_test
        if [ $STATUS == 1 ]; then
            echo "$NAME is Started at pid = $PID"
            exit 0
        fi

        echo -n "Starting $NAME: "

        if [ -n "$ULIMIT" ]; then
            # Set the ulimits
            ulimit $ULIMIT
        fi

        start-stop-daemon --start --quiet --pidfile $PID_FILE --background --make-pidfile --exec "$DAEMON -- $DAEMON_OPTS >> /var/log/$NAME.log 2>&1"

        status_test
        if [ $STATUS==1 ]
        then
            echo "$NAME is Started ; pid is $PID"
            exit 0
        else
            echo "$NAME Start error;"
            exit 1
        fi
}

###
#   stop
###
function stop_daemon(){
        status_test
        if [ $STATUS == 0 ]; then
            echo "$NAME is not in use"
            exit 0
        fi

        echo -n "Stopping $NAME"
        start-stop-daemon --stop --quiet --pidfile $PID_FILE

        status_test
        if [ $STATUS==1 ]
        then
            echo "$NAME. Stoped ;"
            exit 0
        else
            echo "$NAME Stop error;"
            exit 1
        fi
}




###
#  main point
###
case "$1" in
    start)
        start_daemon
        ;;

    stop)
        stop_daemon
        ;;

    restart|force-reload)
        echo -n "Restarting $DESC: "
        start_daemon&&stop_daemon&&echo 'restart succsee'
        ;;

    status)

        status_test

        if [ $STATUS==1 ]
        then
            echo "$NAME is started/starting"
        else
            echo "$NAME is stoped"
        fi
        ;;
    *)
        echo "Usage: $NAME {start|stop|restart}" >&2
        exit 1
        ;;
esac

exit 0