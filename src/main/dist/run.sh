#!/usr/bin/env bash
#
# Goal: update TERMs in the FULL_ANNOT table which have the same TERM_ACC
#  with the TERM_ACC field in the ONT_TERMS table;
# In addition add missing entries in RGD_REF_RGD_ID table for RGD-based annotations
#
. /etc/profile
APPNAME="update-terms-in-full-annot-pipeline"
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

ELIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    ELIST="rgd.devops@mcw.edu jrsmith@mcw.edu"
fi

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/${APPNAME}.jar  "$@" 2>&1 > cron.log

mailx -s "[$SERVER] Update Terms in FULL_ANNOT table" $ELIST < $APPDIR/logs/synopsis.log
