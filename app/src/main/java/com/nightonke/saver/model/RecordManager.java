package com.nightonke.saver.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.nightonke.saver.activity.CoCoinApplication;
import com.nightonke.saver.util.Util;
import com.nightonke.saver.db.DB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cn.bmob.v3.Bmob;
import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.listener.DeleteListener;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * Created by 伟平 on 2015/10/20.
 */

// Todo the update is wrong!

public class RecordManager {

    private static RecordManager recordManager = null;

    private static DB db;

    public static Integer SUM;
    public static List<Record> RECORDS;
    public static List<Tag> TAGS;
    public static Map<Integer, String> TAG_NAMES;

    public static boolean SHOW_LOG = true;
    public static boolean RANDOM_DATA = false;
    private final int RANDOM_DATA_NUMBER_ON_EACH_DAY = 3;
    private final int RANDOM_DATA_EXPENSE_ON_EACH_DAY = 30;

    private static boolean FIRST_TIME = true;

    public static int SAVE_TAG_ERROR_DATABASE_ERROR = -1;
    public static int SAVE_TAG_ERROR_DUPLICATED_NAME = -2;

    public static int DELETE_TAG_ERROR_DATABASE_ERROR = -1;
    public static int DELETE_TAG_ERROR_TAG_REFERENCE = -2;

    private RecordManager(Context context) {
        try {
            db = db.getInstance(context);
            Log.d("Saver", "Loading database successfully.");
        } catch(IOException e) {
            e.printStackTrace();
        }
        if (FIRST_TIME) {
            SharedPreferences preferences =
                    context.getSharedPreferences("Values", Context.MODE_PRIVATE);
            if (preferences.getBoolean("FIRST_TIME", true)) {
                createTags();
                SharedPreferences.Editor editor =
                        context.getSharedPreferences("Values", Context.MODE_PRIVATE).edit();
                editor.putBoolean("FIRST_TIME", false);
                editor.commit();
            }
        }
        if (RANDOM_DATA) {

            SharedPreferences preferences =
                    context.getSharedPreferences("Values", Context.MODE_PRIVATE);
            if (preferences.getBoolean("RANDOM", false)) {
                return;
            }

            Toast.makeText(
                    context, "Creating test data, please wait.", Toast.LENGTH_LONG).show();

            randomDataCreater();

            Toast.makeText(
                    context, "Finish, thanks for testing.", Toast.LENGTH_LONG).show();

            SharedPreferences.Editor editor =
                    context.getSharedPreferences("Values", Context.MODE_PRIVATE).edit();
            editor.putBoolean("RANDOM", true);
            editor.commit();

        }
    }

    public synchronized static RecordManager getInstance(Context context) {
        if (recordManager == null) {
            SUM = 0;
            RECORDS = new LinkedList<>();
            TAGS = new LinkedList<>();
            TAG_NAMES = new HashMap<>();
            recordManager = new RecordManager(context);

            db.getData();

            Log.d("Saver", "Loading " +
                    RECORDS.size() +
                    " records successfully.");
            Log.d("Saver", "Loading " +
                    TAGS.size() +
                    " tags successfully.");

            TAGS.add(0, new Tag(-1, "Sum Histogram", -1));
            TAGS.add(0, new Tag(-2, "Sum Pie", -2));

            for (Tag tag : TAGS) {
                TAG_NAMES.put(tag.getId(), tag.getName());
            }

            sortTAGS();
        }
        return recordManager;
    }

    public static long saveRecord(final Record record) {
        long insertId = -1;
        record.setIsUploaded(false);
        User user = BmobUser.getCurrentUser(CoCoinApplication.getAppContext(), User.class);
        if (user != null) {
            record.setUserId(user.getObjectId());
        } else {
            record.setUserId(null);
        }
        if (RecordManager.SHOW_LOG) {
            Log.d("Saver", "Manager: Save record: " + record.toString());
        }
        insertId = db.saveRecord(record);
        if (insertId == -1) {
            if (RecordManager.SHOW_LOG) {
                Log.d("Saver", "Save the above record FAIL!");
            }
        } else {
            if (RecordManager.SHOW_LOG) {
                Log.d("Saver", "Save the above record SUCCESSFULLY!");
            }
            RECORDS.add(record);
            record.setIsUploaded(false);
            SUM += (int)record.getMoney();
            if (user != null) {
                // already login
                record.setUserId(user.getObjectId());
                record.save(CoCoinApplication.getAppContext(), new SaveListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("Saver", "SSS Save record(" + record.toString() + ") to DB");
                        record.setIsUploaded(true);
                        record.setLocalObjectId(record.getObjectId());
                        db.updateRecord(record);
                    }

                    @Override
                    public void onFailure(int code, String msg) {
                        Log.d("Saver", "FFF Save record(" + record.toString() + ") to DB");
                        Log.d("Saver", msg);
                    }
                });
            } else {

            }
        }
        return insertId;
    }

    public static int saveTag(Tag tag) {
        int insertId = -1;
        if (RecordManager.SHOW_LOG) {
            Log.d("Saver", "Manager: Save tag: " + tag.toString());
        }
        boolean duplicatedName = false;
        for (Tag t : TAGS) {
            if (t.getName().equals(tag.getName())) {
                duplicatedName = true;
                break;
            }
        }
        if (duplicatedName) {
            return SAVE_TAG_ERROR_DUPLICATED_NAME;
        }
        insertId = db.saveTag(tag);
        if (insertId == -1) {
            if (RecordManager.SHOW_LOG) {
                Log.d("Saver", "Save the above tag FAIL!");
                return SAVE_TAG_ERROR_DATABASE_ERROR;
            }
        } else {
            if (RecordManager.SHOW_LOG) {
                Log.d("Saver", "Save the above tag SUCCESSFULLY!");
            }
            TAGS.add(tag);
            TAG_NAMES.put(tag.getId(), tag.getName());
            sortTAGS();
        }
        return insertId;
    }

// delete a record//////////////////////////////////////////////////////////////////////////////////
    public static long deleteRecord(final Record record, boolean deleteInList) {
        long deletedId = -1;
        Log.d("Saver",
                "Manager: Delete record: " + "Record(id = "
                        + record.getId() + ", deletedId = " + deletedId + ")");
        deletedId = db.deleteRecord(record.getId());
        User user = BmobUser.getCurrentUser(CoCoinApplication.getAppContext(), User.class);
        // if we can delete the record from server
        if (user != null && record.getLocalObjectId() != null) {
            record.delete(CoCoinApplication.getAppContext(), new DeleteListener() {
                @Override
                public void onSuccess() {
                    Log.d("Saver", "Delete " + record.toString() + " from server successfully.");
                }
                @Override
                public void onFailure(int code, String msg) {
                    Log.d("Saver", "Delete " + record.toString() + " from server failed.");
                }
            });
        }

        if (deletedId == -1) {
            Log.d("Saver", "Delete the above record FAIL!");
        } else {
            Log.d("Saver", "Delete the above record SUCCESSFULLY!");
            SUM -= (int)record.getMoney();
            if (deleteInList) {
                for (Record r : RECORDS) {
                    if (r.getId() == record.getId()) {
                        RECORDS.remove(record);
                        break;
                    }
                }
            }
        }
        return record.getId();
    }

    public static int deleteTag(int id) {
        int deletedId = -1;
        Log.d("Saver",
                "Manager: Delete tag: " + "Tag(id = " + id + ", deletedId = " + deletedId + ")");
        boolean tagReference = false;
        for (Record record : RECORDS) {
            if (record.getTag() == id) {
                tagReference = true;
                break;
            }
        }
        if (tagReference) {
            return DELETE_TAG_ERROR_TAG_REFERENCE;
        }
        deletedId = db.deleteTag(id);
        if (deletedId == -1) {
            Log.d("Saver", "Delete the above tag FAIL!");
            return DELETE_TAG_ERROR_DATABASE_ERROR;
        } else {
            Log.d("Saver", "Delete the above tag SUCCESSFULLY!");
            for (Tag tag : TAGS) {
                if (tag.getId() == deletedId) {
                    TAGS.remove(tag);
                    break;
                }
            }
            TAG_NAMES.remove(id);
            sortTAGS();
        }
        return deletedId;
    }

    public static long updateRecord(final Record record) {
        long updateId = -1;
        Log.d("Saver",
                "Manager: Update record: " + record.toString());
        updateId = db.updateRecord(record);
        if (updateId == -1) {
            Log.d("Saver", "Update the above record FAIL!");
        } else {
            Log.d("Saver", "Update the above record SUCCESSFULLY!");
            for (Record r : RECORDS) {
                if (r.getId() == record.getId()) {
                    SUM -= (int)r.getMoney();
                    SUM += (int)record.getMoney();
                    r.set(record);
                    break;
                }
            }
            record.setIsUploaded(false);
            User user = BmobUser
                    .getCurrentUser(CoCoinApplication.getAppContext(), User.class);
            if (user != null) {
                // already login
                if (record.getLocalObjectId() != null) {
                    // this record has been push to the server
                    record.setUserId(user.getObjectId());
                    record.update(CoCoinApplication.getAppContext(),
                            record.getLocalObjectId(), new UpdateListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Saver", "SSS Update record(" + record.toString() + ") to DB");
                                    record.setIsUploaded(true);
                                    record.setLocalObjectId(record.getObjectId());
                                    db.updateRecord(record);
                                }

                                @Override
                                public void onFailure(int code, String msg) {
                                    Log.d("Saver", "FFF Update record(" + record.toString() + ") to DB");
                                    Log.d("Saver", msg);
                                }
                            });
                } else {
                    record.setUserId(user.getObjectId());
                    record.save(CoCoinApplication.getAppContext(), new SaveListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Saver", "SSS Update record(" + record.toString() + ") to DB");
                                    record.setIsUploaded(true);
                                    record.setLocalObjectId(record.getObjectId());
                                    db.updateRecord(record);
                                }
                                @Override
                                public void onFailure(int code, String msg) {
                                    Log.d("Saver", "FFF Update record(" + record.toString() + ") to DB");
                                    Log.d("Saver", msg);
                                }
                            });
                }
            } else {
                db.updateRecord(record);
            }
        }
        return updateId;
    }

// update the records changed to server/////////////////////////////////////////////////////////////
    private static boolean isLastOne = false;
    public static long updateOldRecordsToServer() {
        long counter = 0;
        User user = BmobUser
                .getCurrentUser(CoCoinApplication.getAppContext(), User.class);
        if (user != null) {
// already login////////////////////////////////////////////////////////////////////////////////////
            isLastOne = false;
            for (int i = 0; i < RECORDS.size(); i++) {
                if (i == RECORDS.size() - 1) isLastOne = true;
                final Record record = RECORDS.get(i);
                if (!record.getIsUploaded()) {
// has been changed/////////////////////////////////////////////////////////////////////////////////
                    if (record.getLocalObjectId() != null) {
// there is an old record in server, we should update this record///////////////////////////////////
                        record.setUserId(user.getObjectId());
                        record.update(CoCoinApplication.getAppContext(),
                                record.getLocalObjectId(), new UpdateListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d("Saver", "SSS Update record(" + record.toString() + ") to DB");
                                        record.setIsUploaded(true);
                                        record.setLocalObjectId(record.getObjectId());
                                        db.updateRecord(record);
// after updating, get the old records from server//////////////////////////////////////////////////
                                        if (isLastOne) getRecordsFromServer();
                                    }

                                    @Override
                                    public void onFailure(int code, String msg) {
                                        Log.d("Saver", "FFF Update record(" + record.toString() + ") to DB");
                                        Log.d("Saver", msg);
                                    }
                                });
                    } else {
                        counter++;
                        record.setUserId(user.getObjectId());
                        record.save(CoCoinApplication.getAppContext(), new SaveListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("Saver", "SSS Save record(" + record.toString() + ") to DB");
                                record.setIsUploaded(true);
                                record.setLocalObjectId(record.getObjectId());
                                db.updateRecord(record);
// after updating, get the old records from server//////////////////////////////////////////////////
                                if (isLastOne) getRecordsFromServer();
                            }

                            @Override
                            public void onFailure(int code, String msg) {
                                Log.d("Saver", "FFF Save record(" + record.toString() + ") to DB");
                                Log.d("Saver", msg);
                            }
                        });
                    }
                }
            }
        } else {

        }

        Log.d("Saver", "Update " + counter + " records to server.");

        if (RECORDS.size() == 0) getRecordsFromServer();

        return counter;
    }

    public static long updateTag(Tag tag) {
        int updateId = -1;
        Log.d("Saver",
                "Manager: Update tag: " + tag.toString());
        updateId = db.updateTag(tag);
        if (updateId == -1) {
            Log.d("Saver", "Update the above tag FAIL!");
        } else {
            Log.d("Saver", "Update the above tag SUCCESSFULLY!" + " - " + updateId);
            for (Tag t : TAGS) {
                if (t.getId() == tag.getId()) {
                    t.set(tag);
                    break;
                }
            }
            sortTAGS();
        }
        return updateId;
    }

//get records from server to local//////////////////////////////////////////////////////////////////
    private static long updateNum;
    public static long getRecordsFromServer() {
        updateNum = 0;
        BmobQuery<Record> query = new BmobQuery<Record>();
        query.addWhereEqualTo("userId",
                BmobUser.getCurrentUser(CoCoinApplication.getAppContext(), User.class).getObjectId());
        query.setLimit(Integer.MAX_VALUE);
        query.findObjects(CoCoinApplication.getAppContext(), new FindListener<Record>() {
            @Override
            public void onSuccess(List<Record> object) {
                Log.d("Saver", "Get " + object.size() + " records from server");
                updateNum = object.size();
                for (Record record : object) {
                    boolean exist = false;
                    for (int i = RECORDS.size() - 1; i >= 0; i--) {
                        if (record.getObjectId().equals(RECORDS.get(i).getLocalObjectId())) {
                            exist = true;
                            break;
                        }
                    }
                    if (!exist) {
                        Record newRecord = new Record();
                        newRecord.set(record);
                        newRecord.setId(-1);
                        RECORDS.add(newRecord);
                    }
                }

                Collections.sort(RECORDS, new Comparator<Record>() {
                    @Override
                    public int compare(Record lhs, Record rhs) {
                        if (lhs.getCalendar().before(rhs.getCalendar())) {
                            return -1;
                        } else if (lhs.getCalendar().after(rhs.getCalendar())) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                });

                db.deleteAll();

                SUM = 0;
                for (int i = 0; i < RECORDS.size(); i++) {
                    RECORDS.get(i).setLocalObjectId(RECORDS.get(i).getObjectId());
                    RECORDS.get(i).setIsUploaded(true);
                    db.saveRecord(RECORDS.get(i));
                    SUM += (int)RECORDS.get(i).getMoney();
                }

                Log.d("Saver", "Save all " + RECORDS.size() + " records.");
            }
            @Override
            public void onError(int code, String msg) {
                Log.d("Saver", "Query fail: " + msg);
            }
        });

        return updateNum;
    }

    public static int getCurrentMonthExpense() {
        Calendar calendar = Calendar.getInstance();
        Calendar left = Util.GetThisMonthLeftRange(calendar);
        int monthSum = 0;
        for (int i = RECORDS.size() - 1; i >= 0; i--) {
            if (RECORDS.get(i).getCalendar().before(left)) break;
            monthSum += RECORDS.get(i).getMoney();
        }
        return monthSum;
    }

    public static List<Record> queryRecordByTime(Calendar c1, Calendar c2) {
        List<Record> list = new LinkedList<>();
        for (Record record : RECORDS) {
            if (record.isInTime(c1, c2)) {
                list.add(record);
            }
        }
        return list;
    }

    public static List<Record> queryRecordByCurrency(String currency) {
        List<Record> list = new LinkedList<>();
        for (Record record : RECORDS) {
            if (record.getCurrency().equals(currency)) {
                list.add(record);
            }
        }
        return list;
    }

    public static List<Record> queryRecordByTag(int tag) {
        List<Record> list = new LinkedList<>();
        for (Record record : RECORDS) {
            if (record.getTag() == tag) {
                list.add(record);
            }
        }
        return list;
    }

    public static List<Record> queryRecordByMoney(double money1, double money2, String currency) {
        List<Record> list = new LinkedList<>();
        for (Record record : RECORDS) {
            if (record.isInMoney(money1, money2, currency)) {
                list.add(record);
            }
        }
        return list;
    }

    public static List<Record> queryRecordByRemark(String remark) {
        List<Record> list = new LinkedList<>();
        for (Record record : RECORDS) {
            if (Util.IsStringRelation(record.getRemark(), remark)) {
                list.add(record);
            }
        }
        return list;
    }

    private void createTags() {
        saveTag(new Tag(-1, "Meal",                0));
        saveTag(new Tag(-1, "Clothing & Footwear", 1));
        saveTag(new Tag(-1, "Home",                2));
        saveTag(new Tag(-1, "Traffic",             3));
        saveTag(new Tag(-1, "Vehicle Maintenance", 4));
        saveTag(new Tag(-1, "Book",                5));
        saveTag(new Tag(-1, "Hobby",               6));
        saveTag(new Tag(-1, "Internet",            7));
        saveTag(new Tag(-1, "Friend",              8));
        saveTag(new Tag(-1, "Education",           9));
        saveTag(new Tag(-1, "Entertainment",      10));
        saveTag(new Tag(-1, "Medical",            11));
        saveTag(new Tag(-1, "Insurance",          12));
        saveTag(new Tag(-1, "Donation",           13));
        saveTag(new Tag(-1, "Sport",              14));
        saveTag(new Tag(-1, "Snack",              15));
        saveTag(new Tag(-1, "Music",              16));
        saveTag(new Tag(-1, "Fund",               17));
        saveTag(new Tag(-1, "Drink",              18));
        saveTag(new Tag(-1, "Fruit",              19));
        saveTag(new Tag(-1, "Film",               20));
        saveTag(new Tag(-1, "Baby",               21));
        saveTag(new Tag(-1, "Partner",            22));
        saveTag(new Tag(-1, "Housing Loan",       23));
        saveTag(new Tag(-1, "Pet",                24));
        saveTag(new Tag(-1, "Telephone Bill",     25));
        saveTag(new Tag(-1, "Travel",             26));
        sortTAGS();
    }

    private void randomDataCreater() {

        Random random = new Random();

        List<Record> createdRecords = new ArrayList<>();

        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.set(2015, 0, 1, 0, 0, 0);
        c.add(Calendar.SECOND, 1);

        while (c.before(now)) {
            for (int i = 0; i < RANDOM_DATA_NUMBER_ON_EACH_DAY; i++) {
                Calendar r = (Calendar)c.clone();
                int hour = random.nextInt(24);
                int minute = random.nextInt(60);
                int second = random.nextInt(60);

                r.set(Calendar.HOUR_OF_DAY, hour);
                r.set(Calendar.MINUTE, minute);
                r.set(Calendar.SECOND, second);
                r.add(Calendar.SECOND, 0);

                int tag = random.nextInt(TAGS.size());
                int expense = random.nextInt(RANDOM_DATA_EXPENSE_ON_EACH_DAY) + 1;

                Record record = new Record();
                record.setCalendar(r);
                record.setMoney(expense);
                record.setTag(tag);
                record.setCurrency("RMB");
                record.setRemark("备注：" + record.toString());

                createdRecords.add(record);
            }
            c.add(Calendar.DATE, 1);
        }

        Collections.sort(createdRecords, new Comparator<Record>() {
            @Override
            public int compare(Record lhs, Record rhs) {
                if (lhs.getCalendar().before(rhs.getCalendar())) {
                    return -1;
                } else if (lhs.getCalendar().after(rhs.getCalendar())) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        for (Record record : createdRecords) {
            saveRecord(record);
        }
    }

    // Todo bug here
    private static void sortTAGS() {
        Collections.sort(TAGS, new Comparator<Tag>() {
            @Override
            public int compare(Tag lhs, Tag rhs) {
                if (lhs.getWeight() != rhs.getWeight()) {
                    return Integer.valueOf(lhs.getWeight()).compareTo(rhs.getWeight());
                } else if (!lhs.getName().equals(rhs.getName())) {
                    return lhs.getName().compareTo(rhs.getName());
                } else {
                    return Integer.valueOf(lhs.getId()).compareTo(rhs.getId());
                }
            }
        });
    }

}
