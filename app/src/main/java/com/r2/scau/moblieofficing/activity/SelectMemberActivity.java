package com.r2.scau.moblieofficing.activity;

import android.content.Intent;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.bigkoo.quicksidebar.QuickSideBarTipsView;
import com.bigkoo.quicksidebar.QuickSideBarView;
import com.bigkoo.quicksidebar.listener.OnQuickSideBarTouchListener;
import com.r2.scau.moblieofficing.Contants;
import com.r2.scau.moblieofficing.R;
import com.r2.scau.moblieofficing.adapter.SelectMemberAdapter;
import com.r2.scau.moblieofficing.bean.ChatRecord;
import com.r2.scau.moblieofficing.bean.Contact;
import com.r2.scau.moblieofficing.smack.SmackListenerManager;
import com.r2.scau.moblieofficing.smack.SmackManager;
import com.r2.scau.moblieofficing.smack.SmackMultiChatManager;
import com.r2.scau.moblieofficing.untils.DateUtil;
import com.r2.scau.moblieofficing.untils.OkHttpUntil;
import com.r2.scau.moblieofficing.untils.UserUntil;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration;

import org.greenrobot.eventbus.EventBus;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.Request;

import static com.r2.scau.moblieofficing.Contants.SELECT_MEMBER_REPORT;
import static com.r2.scau.moblieofficing.untils.OkHttpUntil.okHttpClient;
import static com.r2.scau.moblieofficing.untils.UserUntil.friendList;

public class SelectMemberActivity extends BaseActivity implements OnQuickSideBarTouchListener {

    private Toolbar mToolbar;
    private TextView mTitleTV;
    private String groupName;
    private int type;
    private RecyclerView mRecyclerView;
    private SelectMemberAdapter adapter;
    private QuickSideBarView mQuickSideBarView;
    private QuickSideBarTipsView mQuickSideBarTipsView;
    private List<Contact> mContactList = new ArrayList<>();
    private HashMap<String, Integer> letters = new HashMap<>();


    @Override
    protected void initView() {
        setContentView(R.layout.activity_contact);

        Intent intent = getIntent();
        type = intent.getIntExtra("type", -1);

        mRecyclerView = (RecyclerView) findViewById(R.id.rv_contact);
        mQuickSideBarView = (QuickSideBarView) findViewById(R.id.qsbv);
        mQuickSideBarTipsView = (QuickSideBarTipsView) findViewById(R.id.qsbtv);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mTitleTV = (TextView) findViewById(R.id.toolbar_title);

        mToolbar.setTitle("");
        mTitleTV.setText("选择群成员");

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }


    @Override
    protected void initData() {
        Intent intent = getIntent();
        groupName = intent.getStringExtra("groupName");
        ArrayList<String> customLetters = new ArrayList<>();
        mContactList = friendList;
        Collections.sort(mContactList);
        int position = 0;
        for (Contact contact : mContactList) {
            String letter = contact.getFirstLetter();
            if (!letters.containsKey(letter)) {
                letters.put(letter, position);
                customLetters.add(letter);
            }
            position++;
        }
        initRV(customLetters);
    }

    public void initRV(ArrayList<String> customLetters) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);

        adapter = new SelectMemberAdapter();
        mQuickSideBarView.setLetters(customLetters);
        adapter.addAll(mContactList);

        // Add the sticky headers decoration
        StickyRecyclerHeadersDecoration headersDecor = new StickyRecyclerHeadersDecoration(adapter);

        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addItemDecoration(headersDecor);
    }


    @Override
    protected void initListener() {
        mQuickSideBarView.setOnQuickSideBarTouchListener(this);
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_select_member_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.menu_select_member:
                if (type == SELECT_MEMBER_REPORT) {
                    ArrayList<Contact> selectMember = new ArrayList<>();
                    for (Contact contact : mContactList) {
                        if (contact.isSelect() == true) {
                            selectMember.add(contact);
                        }
                    }
                    Intent intent = new Intent();
                    intent.putParcelableArrayListExtra("member", selectMember);
                    setResult(Contants.ACTIVIRY_SELECT_MEMBER_RETURN_RESULT, intent);
                    finish();
                } else {

                    List<Contact> selectMember = new ArrayList<>();
                    MultiUserChat multiUserChat;
                    Intent intent = getIntent();
                    String groupName = intent.getStringExtra("groupName");
                    String reason = String.format("%s邀请你入群", UserUntil.gsonUser.getNickname());
                    try {
                        multiUserChat = SmackManager.getInstance().createChatRoom(groupName, UserUntil.gsonUser.getNickname(), null);
                        SmackListenerManager.addMultiChatMessageListener(multiUserChat);
                        SmackMultiChatManager.saveMultiChat(multiUserChat);
                        FormBody formBody = new FormBody.Builder()
                                .add("userPhone", UserUntil.gsonUser.getUserPhone())
                                .add("groupName", groupName)
                                .build();
//                        step 3: 创建请求
                        Request request = new Request.Builder().url("http://192.168.13.61:8089/group/createGroup.shtml")
                                .post(formBody)
                                .addHeader("cookie", OkHttpUntil.loginSessionID)
                                .build();

//                        step 4： 建立联系 创建Call对象
                        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
                            @Override
                            public void onFailure(okhttp3.Call call, IOException e) {
//                                 TODO: 17-1-4  请求失败
                                Log.e("register", "fail");
                            }

                            @Override
                            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
//                                 TODO: 17-1-4 请求成功
                                String str = response.body().string();
                                Log.e("register", str);
                            }
                        });
                        for (Contact contact : mContactList) {
                            if (contact.isSelect() == true) {
                                selectMember.add(contact);
                                String jid = SmackManager.getInstance().getFullJid(contact.getPhone());
                                multiUserChat.invite(jid, reason);//邀请入群
                                formBody = new FormBody.Builder()
                                        .add("groupCreatedUserPhone", UserUntil.gsonUser.getUserPhone())
                                        .add("groupName", groupName)
                                        .add("userPhone", contact.getPhone())
                                        .build();
//                            step 3: 创建请求
                                request = new Request.Builder().url("http://192.168.13.61:8089/group/joinGroup.shtml")
                                        .post(formBody)
                                        .addHeader("cookie", OkHttpUntil.loginSessionID)
                                        .build();

//                        step 4： 建立联系 创建Call对象
                                okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
                                    @Override
                                    public void onFailure(okhttp3.Call call, IOException e) {
//                                 TODO: 17-1-4  请求失败
                                        Log.e("register", "fail");
                                    }

                                    @Override
                                    public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
//                                 TODO: 17-1-4 请求成功
                                        String str = response.body().string();
                                        Log.e("register", str);
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    String roomName = groupName + "@conference." + SmackManager.SERVER_NAME;
                    ChatRecord record;
                    List<ChatRecord> chatRecords = DataSupport.where("mfriendusername=?", roomName).find(ChatRecord.class);
                    if (chatRecords.size() == 0) {
                        record = new ChatRecord();
                        String friendUserName = roomName;
                        int idx = friendUserName.indexOf("@conference.");
                        String friendNickName = friendUserName.substring(0, idx);
                        record.setUuid(UUID.randomUUID().toString());
                        record.setmFriendUsername(friendUserName);
                        record.setmFriendNickname(friendNickName);
                        record.setmMeUsername(UserUntil.gsonUser.getUserPhone());
                        record.setmMeNickname(UserUntil.gsonUser.getNickname());
                        record.setmChatTime(DateUtil.currentDatetime());
                        record.setmIsMulti(true);
                        record.setmChatJid(roomName);
                        record.save();
                        SmackManager.getInstance().joinChatRoom(roomName, UserUntil.gsonUser.getNickname(), null);
                    } else {
                        record = chatRecords.get(0);
                    }
                    EventBus.getDefault().post(record);
                    Intent startChat = new Intent(getApplicationContext(), ChatActivity.class);
                    startChat.putExtra("chatrecord", record);
                    startActivity(startChat);
                    finish();
                }

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        mQuickSideBarTipsView.setText(letter, position, y);
        //有此key则获取位置并滚动到该位置
        if (letters.containsKey(letter)) {
            mRecyclerView.scrollToPosition(letters.get(letter));
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        //可以自己加入动画效果渐显渐隐
        mQuickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }
}
