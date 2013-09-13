#!/bin/sh
if [ $# -lt 1 ]; then
        echo "not enough arguments"
        exit 1
fi

LOCAL_ID=$1
export LOG_DIR=$2
OPTS="-server"
LOCAL=`hostname`


#OPTS="${OPTS} -agentlib:hprof=cpu=samples,interval=10,depth=5"

CLASSPATH="${HOME}/@@DIR@@/:${HOME}/@@DIR@@/jars/*:"


OPTS="${OPTS} -cp ${CLASSPATH}"
# TODO switch protocol based on properties file
OPTS="${OPTS} -Dprotocols.conf.url=file://${LOG_DIR}/protocols.conf "
OPTS="${OPTS} -Dnodes.list.url=file://${LOG_DIR}/nodes "
OPTS="${OPTS} -Dlsr.constants.file=file://${LOG_DIR}/properties "

COMMAND="java ${OPTS} ch.epfl.lsr.distal.deployment.DSLProtocolRunner ${LOCAL_ID}"

(echo ID: ${LOCAL_ID} LOGDIR: ${LOG_DIR} HOST: ${LOCAL}
    echo Starting at `date`
echo running ${COMMAND}
/usr/bin/time -v ${COMMAND}
echo Finished at `date`
) >> ${LOG_DIR}/${LOCAL_ID}.log 2>${LOG_DIR}/${LOCAL_ID}.err.log
