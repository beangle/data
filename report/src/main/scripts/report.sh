#!/bin/sh
if [ "$1" = "" ] ; then
   echo "Ussage:report.sh /path/to/your/report.xml"
else
   java -cp "lib/*" org.beangle.data.report.Reporter "$1"
fi


