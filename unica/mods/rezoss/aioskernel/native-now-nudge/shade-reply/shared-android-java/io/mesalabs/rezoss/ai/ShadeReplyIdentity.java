package io.mesalabs.rezoss.ai;

import android.app.Notification;
import android.app.Person;
import android.text.TextUtils;
import android.view.textclassifier.ConversationActions;
import java.util.List;

/** Same latest incoming raw text/time used by the installed notification assistant. */
public final class ShadeReplyIdentity {
    private ShadeReplyIdentity() {}
    public static String request(ConversationActions.Request request) {
        List<ConversationActions.Message> messages=request.getConversation();
        if(messages==null||messages.isEmpty())return null;
        ConversationActions.Message last=messages.get(messages.size()-1);
        if(same(ConversationActions.Message.PERSON_USER_SELF,last.getAuthor())||last.getReferenceTime()==null||last.getText()==null)return null;
        return ShadeReplyContract.fingerprint(last.getText().toString(),last.getReferenceTime().toInstant().toEpochMilli());
    }
    public static String notification(Notification notification) {
        if(notification == null || notification.extras == null)return null;
        List<Notification.MessagingStyle.Message> messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES));
        if(messages==null||messages.isEmpty())return null;
        Notification.MessagingStyle.Message last=messages.get(messages.size()-1);
        Person sender=last.getSenderPerson(),self=notification.extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person.class);
        if(self==null||sender==null||same(sender,self)||last.getText()==null)return null;
        return ShadeReplyContract.fingerprint(last.getText().toString(),last.getTimestamp());
    }
    private static boolean same(Person a,Person b) {
        return b!=null&&TextUtils.equals(a.getKey(),b.getKey())&&TextUtils.equals(a.getName(),b.getName())&&TextUtils.equals(a.getUri(),b.getUri());
    }
}
