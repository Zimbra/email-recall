import { createElement, Component } from 'preact';
import { withIntl } from '../../enhancers';
import {  ActionMenuItem } from '@zimbra-client/components';
import { withText } from 'preact-i18n';

@withIntl()
@withText({
    title: 'zimbra-zimlet-undosend.title',
    fail: 'zimbra-zimlet-undosend.fail',
    conversation: 'zimbra-zimlet-undosend.conversation',
})
export default class MoreMenu extends Component {
    constructor(props) {
        super(props);
        this.zimletContext = props.children.context;
    };

    handleClick = e => {
        //this.props.emailData contains a JSON-like object with the clicked email's data, you could use the id to fetch the email from the REST API from the back-end
        if(this.props.emailData.__typename == 'Conversation')
        {
            this.toaster(this.props.conversation);
            return;
        }

        var request = new XMLHttpRequest();
        var url = this.zimletContext.zimbraOrigin + '/service/extension/undosend';
        var formData = new FormData();       
        formData.append("jsondata", JSON.stringify(this.props.emailData));
        request.open('POST', url);
        request.onreadystatechange = function (e) {
            if (request.readyState == 4) {
                if (request.status == 200) {
                    //no UI feedback, user gets confirmation email
                }
                else {
                    this.toaster(this.props.fail);
                }
            }
        }.bind(this);
        request.send(formData);
    }

    toaster = (message) => {
        const { dispatch } = this.zimletContext.store;
        dispatch(this.zimletContext.zimletRedux.actions.notifications.notify({
            message: message
        }));
    }

    render() {
       if(parent.window.location.href.indexOf('\/email\/Sent\/')>-1)
       {
          return (
            <div>
                <ActionMenuItem onClick={this.handleClick}>
                    {this.props.title}
                </ActionMenuItem>
            </div>
          );
        }
    }
}
