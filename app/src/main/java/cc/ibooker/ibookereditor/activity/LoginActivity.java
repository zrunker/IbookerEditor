package cc.ibooker.ibookereditor.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import cc.ibooker.ibookereditor.R;
import cc.ibooker.ibookereditor.base.BaseActivity;
import cc.ibooker.ibookereditor.dto.ResultData;
import cc.ibooker.ibookereditor.dto.UserDto;
import cc.ibooker.ibookereditor.event.MainReflashHeaderEvent;
import cc.ibooker.ibookereditor.event.UpdateUserInfoSuccessEvent;
import cc.ibooker.ibookereditor.net.service.HttpMethods;
import cc.ibooker.ibookereditor.sqlite.SQLiteDao;
import cc.ibooker.ibookereditor.sqlite.SQLiteDaoImpl;
import cc.ibooker.ibookereditor.utils.ClickUtil;
import cc.ibooker.ibookereditor.utils.ConstantUtil;
import cc.ibooker.ibookereditor.utils.NetworkUtil;
import cc.ibooker.ibookereditor.utils.ToastUtil;
import cc.ibooker.zdialoglib.ProgressDialog;
import rx.Subscriber;
import rx.subscriptions.CompositeSubscription;

/**
 * 登录界面
 *
 * @author 邹峰立
 */
public class LoginActivity extends BaseActivity implements View.OnClickListener, TextView.OnEditorActionListener {
    private EditText accountEd, passwdEd;
    private ProgressDialog proDialog;
    private Subscriber<ResultData<UserDto>> userLoginSubscriber;
    private CompositeSubscription mSubscription;
    private SQLiteDao sqLiteDao;

    private final int FROM_LOGIN_TO_REG_CODE = 222;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setStatusBarColor(android.R.color.transparent);

        init();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userLoginSubscriber != null)
            userLoginSubscriber.unsubscribe();
        closeProDialog();
    }

    @Override
    public void finish() {
        // 发送通讯
        EventBus.getDefault().postSticky(new MainReflashHeaderEvent(true));
        setResult(RESULT_OK);
        super.finish();
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSubscription != null) {
            mSubscription.clear();
            mSubscription.unsubscribe();
        }
    }

    // 初始化控件
    private void init() {
        accountEd = findViewById(R.id.ed_account);
        passwdEd = findViewById(R.id.ed_passwd);
        passwdEd.setOnEditorActionListener(this);
        Button loginBtn = findViewById(R.id.btn_login);
        loginBtn.setOnClickListener(this);
        TextView accountRegTv = findViewById(R.id.tv_account_reg);
        accountRegTv.setOnClickListener(this);
        TextView forgetPasswdTv = findViewById(R.id.tv_forget_passwd);
        forgetPasswdTv.setOnClickListener(this);
    }

    // 点击事件监听
    @Override
    public void onClick(View v) {
        if (ClickUtil.isFastClick()) return;
        switch (v.getId()) {
            case R.id.btn_login:
                login();
                break;
            case R.id.tv_account_reg:// 注册
                Intent intentReg = new Intent(this, RegActivity.class);
                startActivityForResult(intentReg, FROM_LOGIN_TO_REG_CODE);
                break;
            case R.id.tv_forget_passwd:// 忘记密码
                Intent intentForgetPasswd = new Intent(this, ForgetPasswdOneActivity.class);
                startActivity(intentForgetPasswd);
                break;
        }
    }

    // 对enter点击事件监听
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            // 执行登录功能
            login();
            return true;
        }
        return false;
    }

    // 登录方法
    private void login() {
        String uPhone = accountEd.getText().toString().trim();
        String uPasswd = passwdEd.getText().toString().trim();
        if (TextUtils.isEmpty(uPhone)) {
            ToastUtil.shortToast(this, "请输入账号");
        } else if (uPhone.length() != 11) {
            ToastUtil.shortToast(this, "请输入正确格式的手机号");
        } else if (TextUtils.isEmpty(uPasswd)) {
            ToastUtil.shortToast(this, "请输入密码");
        } else {
            // 提交到后台实现登录
            userLogin(uPhone, uPasswd);
        }
    }

    /**
     * 登录
     */
    private void userLogin(String account, String uPasswd) {
        if (NetworkUtil.isNetworkConnected(this)) {
            showProDialog();
            userLoginSubscriber = new Subscriber<ResultData<UserDto>>() {
                @Override
                public void onCompleted() {
                    closeProDialog();
                }

                @Override
                public void onError(Throwable e) {
                    ToastUtil.shortToast(LoginActivity.this, e.getMessage());
                    closeProDialog();
                }

                @Override
                public void onNext(ResultData<UserDto> userDtoResultData) {
                    if (userDtoResultData.getResultCode() == 0) {// 成功
                        if (userDtoResultData.getData() == null) {
                            ToastUtil.shortToast(LoginActivity.this, "获取数据失败！");
                        } else {
                            // 静态赋值
                            ConstantUtil.userDto = userDtoResultData.getData();
                            // 将数据保存到数据库
                            if (sqLiteDao == null)
                                sqLiteDao = new SQLiteDaoImpl(LoginActivity.this);
                            sqLiteDao.insertUser(userDtoResultData.getData());
                            finish();
                        }
                    } else {// 失败
                        ToastUtil.shortToast(LoginActivity.this, userDtoResultData.getResultMsg());
                    }
                }
            };
            HttpMethods.getInstance().userLogin(userLoginSubscriber, account, uPasswd);
            if (mSubscription == null)
                mSubscription = new CompositeSubscription();
            mSubscription.add(userLoginSubscriber);
        } else {// 无网络
            ToastUtil.shortToast(this, "当前网络不给力！");
        }
    }

    // 开启Dialog
    private void showProDialog() {
        if (proDialog == null)
            proDialog = new ProgressDialog(this);
        proDialog.showProDialog();
    }

    // 关闭Dialog
    private void closeProDialog() {
        if (proDialog != null)
            proDialog.closeProDialog();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case FROM_LOGIN_TO_REG_CODE:// 注册返回
                    EventBus.getDefault().postSticky(new UpdateUserInfoSuccessEvent(true));
                    finish();
                    break;
            }
        }
    }
}
