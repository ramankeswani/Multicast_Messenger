package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Keswani on 2/25/2018.
 */

/*
Referred Android Developer Guide For SQLiteOpenHelper
https://developer.android.com/
https://developer.android.com/reference/android/arch/persistence/db/SupportSQLiteOpenHelper.html
 */


public class GroupMessengerDBHelper  extends SQLiteOpenHelper{

    GroupMessengerDBHelper(Context context){
        super(context, GroupMessengerConstants.DB_NAME, null,GroupMessengerConstants.DB_VERSION );
    }

    @Override
    public void onCreate(SQLiteDatabase db){
        String CREATE_TABLE = "CREATE TABLE " + GroupMessengerConstants.TABLE_NAME + " (" +
                GroupMessengerConstants.COLUMN_KEY + " TEXT PRIMARY KEY ON CONFLICT REPLACE, " +
                GroupMessengerConstants.COLUMN_VALUE + " TEXT);";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int vOld, int vNew){
        db.execSQL("DROP TABLE IF EXISTS " + GroupMessengerConstants.TABLE_NAME);
        onCreate(db);
    }
}
