package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.ArrayList;

/**
 * Created by Keswani on 2/27/2018.
 */

public class QueueItem {

    String messageID;
    int seqNum;
    int pRec;
    boolean isDeliverable;
    String message;
    ArrayList<String> propRec = new ArrayList<String>();

    QueueItem(String ID, int seq, String message){
        this.messageID = ID;
        this.seqNum = seq;
        pRec = 0;
        isDeliverable = false;
        this.message = message;
    }
    QueueItem(String ID, int seq){
        this.messageID = ID;
        this.seqNum = seq;
        pRec = 0;
        isDeliverable = false;
    }

}
