# Undo Send Zimlet

The undo send Zimlet is a proof of concept not-production ready Zimlet. It allows a user to recall a message that has already been sent. The Zimlet works by running a `zmmboxsearch` on your Zimbra server and look for messages with the message-id of the message to recall. It will remove the message from all mailboxes and place a notification message telling the users a message has been recalled. The Zimlet can not undo the sending of messages that are already on 3rd party mail servers.

## Installing

To perform the installation you can run the auto installation script as root as follows:

```
wget https://raw.githubusercontent.com/Zimbra/undosend/main/undo-send-installer.sh -O /tmp/undo-send-installer.sh
chmod +x /tmp/undo-send-installer.sh
/tmp/undo-send-installer.sh
```

## Usage from CLI

You can use this Zimlet from the command line as zimbra user: 

```
/usr/local/sbin/undosend 953242361.435.1586347147822.JavaMail.zimbra@mind.zimbra.io
```

So use value from Message-Id header without <>. Please note, this command line script does not check permissions, so it will remove the mails with the requested message-id if it finds them.

## Java Extension

The java extension for use with the Zimlet does verify the From header against the user's account email address and aliasses.

## Change the undo send time limit

To change the time in which people can undo send, change `after:-5minutes` to your desired value in the query in undosend script `/usr/local/sbin/undosend`.
