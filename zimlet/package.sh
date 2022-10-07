#!/bin/bash

npm install
zimlet build
zimlet package -v 0.0.1 --zimbraXVersion ">=2.0.0" -n "zimbra-zimlet-undosend" --desc "Undo the sending of an email message." -l "Undo send"
