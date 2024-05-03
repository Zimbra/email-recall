#!/bin/bash

WHO=`whoami`
if [ $WHO != "root" ]
then
echo
echo "Execute this scipt as root (\"sudo su\")"
echo
exit 1
fi

ln -s /bin/echo /usr/bin/echo
ln -s /bin/sed /usr/bin/sed

apt -y install wget uuid-runtime
yum install -y wget util-linux

rm -Rf /opt/zimbra/lib/ext/email-recall
mkdir -p /opt/zimbra/lib/ext/email-recall
wget https://github.com/Zimbra/email-recall/releases/download/0.0.3/extension.jar -O /opt/zimbra/lib/ext/email-recall/email-recall.jar

wget https://github.com/Zimbra/email-recall/releases/download/0.0.3/email-recall -O /usr/local/sbin/email-recall
chmod +x /usr/local/sbin/email-recall

wget https://github.com/Zimbra/email-recall/releases/download/0.0.3/zimbra-zimlet-email-recall.zip -O /tmp/zimbra-zimlet-email-recall.zip
chown zimbra:zimbra /tmp/zimbra-zimlet-email-recall.zip

wget https://github.com/Zimbra/email-recall/releases/download/0.0.3/com_zimbra_email-recall.zip -O /tmp/com_zimbra_email-recall.zip
chown zimbra:zimbra /tmp/com_zimbra_email-recall.zip


su zimbra -c "/opt/zimbra/bin/zmzimletctl deploy /tmp/zimbra-zimlet-email-recall.zip"
su zimbra -c "/opt/zimbra/bin/zmzimletctl deploy /tmp/com_zimbra_email-recall.zip"
su zimbra -c "/opt/zimbra/bin/zmmailboxdctl restart"
