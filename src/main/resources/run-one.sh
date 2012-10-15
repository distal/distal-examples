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
OPTS="${OPTS} -Dprotocols.conf.url=file://${LOG_DIR}/protocols.conf "
OPTS="${OPTS} -Dnodes.list.url=file://${LOG_DIR}/nodes "


(echo ID: ${LOCAL_ID} LOGDIR: ${LOG_DIR}
echo java $OPTS ch.epfl.lsr.distal.DSLProtocolRunner ${LOCAL_ID};
time java $OPTS ch.epfl.lsr.distal.DSLProtocolRunner ${LOCAL_ID} 
) >> ${LOG_DIR}/${LOCAL_ID}.log 2>&1
