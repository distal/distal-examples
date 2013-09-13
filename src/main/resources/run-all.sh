#######
: ${OAR_NODE_FILE:="${HOME}/nodelist"}
: ${OAR_JOB_ID:=$$}
#######
JOB_ID=${OAR_JOB_ID}
echo RUNNING JOB ${JOB_ID}
echo starting at `date`

LOGDIR=${HOME}/logs/$JOB_ID.$$
rm -rf ${LOGDIR}
mkdir -p ${LOGDIR}

LOCAL=`hostname`
sort -u ${OAR_NODE_FILE} > ${LOGDIR}/nodes
NODES=`cat ${LOGDIR}/nodes`
REMOTES=`grep -v -e ${LOCAL} ${LOGDIR}/nodes`

function do_killall ()
{
    echo killing local
    killall -q java 2>&1 > /dev/null
    for node in ${NODES}; do
        echo "killing ${node}"
        oarsh ${node} "bash -c \"killall -q java 2>&1 > /dev/null \""
    done
}

function on_exit ()
{
    echo SHUTDOWN in PROGRESS
    eval do_killall
    sleep 3
    echo END of RUN $JOB_ID
}

#trap on_exit EXIT
do_killall

if [ -e ${HOME}/@@DIR@@/protocols.conf ]; then
    cp ${HOME}/@@DIR@@/protocols.conf ${LOGDIR}
fi

if [ -e ${HOME}/@@DIR@@/properties ]; then
    cp ${HOME}/@@DIR@@/properties ${LOGDIR}
    echo running with properties:
    cat ${HOME}/@@DIR@@/properties
    X=`grep PROTOCOL= ${HOME}/@@DIR@@/properties`
    if [ -n $X ]; then
        eval $X
        if [ -e ${HOME}/@@DIR@@/${PROTOCOL}.protocols.conf ]; then
            cp ${HOME}/@@DIR@@/${PROTOCOL}.protocols.conf ${LOGDIR}/protocols.conf
        else
            echo "${PROTOCOL}.protocols.conf not found";
            exit -1
        fi
    fi
fi


id=1
for remote in ${NODES}; do
    if [ ${remote} !=  ${LOCAL} ]; then
        echo starting @ ${remote}
        oarsh ${remote} "@@DIR@@/run-one.sh ${id} ${LOGDIR}" &
    else
        LOCAL_id=${id}
    fi
    id=$((id+1))
done

echo starting @ ${LOCAL}

oarsh ${LOCAL} "@@DIR@@/run-one.sh ${LOCAL_id} ${LOGDIR}"

echo finished locally at `date`
sleep 3
