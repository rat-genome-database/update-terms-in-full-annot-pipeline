#!/usr/bin/env bash
#
# Goal: update TERMs in the FULL_ANNOT table which have the same TERM_ACC
#  with the TERM_ACC field in the ONT_TERMS table;
# In addition add missing entries in RGD_REF_RGD_ID table for RGD-based annotations
#
. /etc/profile
APPNAME=updateTermsInFULLANNOT
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

ELIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    ELIST="rgd.developers@mcw.edu,jrsmith@mcw.edu"
fi

DB_OPTS="-Dspring.config=$APPDIR/../properties/default_db.xml"
LOG4J_OPTS="-Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml"
declare -x "UPDATE_TERMS_IN_FULLANNOT_OPTS=$DB_OPTS $LOG4J_OPTS"
bin/$APPNAME "$@" 2>&1 > cron.log

mailx -s "[$SERVER] Update Terms in FULL_ANNOT table" rgd.developers@mcw.edu,jrsmith@mcw.edu < $APPDIR/log/summary.log
