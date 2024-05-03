/*.
Copyright (C) 2015-2023  Barry de Graaff

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/.
*/
/**
 * This zimlet checks for X-Vade-Undo Send message header and displays unsubscribe button when found.
 */
function com_zimbra_email_recall_HandlerObject() {
}

com_zimbra_email_recall_HandlerObject.prototype = new ZmZimletBase();
com_zimbra_email_recall_HandlerObject.prototype.constructor = com_zimbra_email_recall_HandlerObject;

/**
 * Simplify handler object
 */
var EmailRecallZimlet = com_zimbra_email_recall_HandlerObject;

/**
 * Initializes the zimlet.
 */
EmailRecallZimlet.prototype.init =
function() {
};

EmailRecallZimlet.prototype._handleEmailRecallZimletMenuClick = function(controller) {
   var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_email_recall').handlerObject;
   //Get selected mail message
   var items = controller.getSelection();
   if(!items instanceof Array) {
      return;
   }
   
   var type = items[0].type;
   var msg;
   if (type == ZmId.ITEM_CONV) {
      msg = items[0].getFirstHotMsg();
   } else if(type == ZmId.ITEM_MSG) {
      msg = items[0];
   }

   var emailData = {};
   emailData.id = msg.id;

   var request = new XMLHttpRequest();
   var url = '/service/extension/undosend';
   var formData = new FormData();       
   formData.append("jsondata", JSON.stringify(emailData));
   request.open('POST', url);
   request.onreadystatechange = function (e) {
      if (request.readyState == 4) {
          if (request.status == 200) {
              //no UI feedback, user gets confirmation email
          }
          else {
              zimletInstance.status('Failed to undo send');
          }
      }
   }.bind(this);
   request.send(formData);


};

/** This method shows a `ZmToast` status message. That fades in and out in a few seconds.
 * @param {string} text - the message to display
 * @param {string} type - the style of the message e.g. ZmStatusView.LEVEL_INFO, ZmStatusView.LEVEL_WARNING, ZmStatusView.LEVEL_CRITICAL
 * */
EmailRecallZimlet.prototype.status = function(text, type) {
   var transitions = [ ZmToast.FADE_IN, ZmToast.PAUSE, ZmToast.PAUSE, ZmToast.PAUSE, ZmToast.FADE_OUT ];
   appCtxt.getAppController().setStatusMsg(text, type, null, transitions);
}; 

/* We are create the toolbar button in onMsgView, normally this is done using initializeToolbar,
 * but initializeToolbar is an event that is generated on various places in Zimbra (compose, etc)
 * Since we only want to add the button in the Zimbra Message View, this is a lot safer.
 * Also this avoids having to deal with multiple instances of toolbars and buttons.
 * 
 * */
EmailRecallZimlet.prototype.onMsgView = function (msg, oldMsg, msgView) { 
   try {
      var app = appCtxt.getCurrentApp();
      var controller = app.getMailListController();
      var toolbar = controller.getCurrentToolbar();
      if (toolbar)
      {
         //When the user forwards emails as eml with attachments, there will be a toolbar, but that one
         //has no getButton method... resulting in a pop-up where the attachments cannot be clicked
         try {
            var getButton = toolbar.getButton('EmailRecallZimletButton')
         } catch (err) {}
         
         if ((getButton) && (!getButton.isDisposed() ))
         {
            //button already defined
         }
         else
         {
            //create app button
            var buttonArgs = {
               text    : 'Undo Send',
               tooltip: 'Undo the sending of this email',
               index: 8, //position of the button
               image: "zimbraicon", //icon
               enabled: true //default if undefined is true, defining it for documentation purpose
            };
            var button = toolbar.createOp("EmailRecallZimletButton", buttonArgs);
            button.addSelectionListener(new AjxListener(this, this._handleEmailRecallZimletMenuClick, controller));
         }
      }      
   } catch (err) {}

   //Only work on messages in the Sent folder
   if(msg.folderId !== "5")
   {
      var button = toolbar.getButton('EmailRecallZimletButton');  
      button.dispose();
      button.setEnabled(false); 
      return;
   }
} 

