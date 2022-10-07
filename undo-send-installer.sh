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

apt -y install wget

rm -Rf /opt/zimbra/lib/ext/undosend
mkdir -p /opt/zimbra/lib/ext/undosend
wget https://github.com/Zimbra/undosend/releases/download/0.0.1/extension.jar -O /opt/zimbra/lib/ext/undosend/undosend.jar

wget https://github.com/Zimbra/undosend/releases/download/0.0.1/undosend -O /usr/local/sbin/undosend
chmod +x /usr/local/sbin/undosend

wget https://github.com/Zimbra/undosend/releases/download/0.0.1/zimbra-zimlet-undosend.zip -O /tmp/zimbra-zimlet-undosend.zip
chown zimbra:zimbra /tmp/zimbra-zimlet-undosend.zip

su zimbra -c "/opt/zimbra/bin/zmzimletctl deploy /tmp/zimbra-zimlet-undosend.zip"
su zimbra -c "/opt/zimbra/bin/zmmailboxdctl restart"
