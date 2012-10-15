

#######
#######
JOB_ID=$OAR_JOB_ID
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

(cd ${LOGDIR}; jar xf ${HOME}/@@DIR@@/jars/@@JAR@@ protocols.conf)
#PROTOCONF=${LOGDIR}/protocols.conf
# id=1
# for node in ${NODES}; do
#     echo "${id} lsr://${protoclass}@${node}:${port}/${protoid} " >> ${PROTOCONF}
#     id=$((id+1))
# done


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


