#!/bin/bash

npm install
zimlet build
zimlet package -v 0.0.1 --zimbraXVersion ">=2.0.0" -n "zimbra-zimlet-email-recall" --desc "Recall the sending of an email message." -l "Email Recall"
