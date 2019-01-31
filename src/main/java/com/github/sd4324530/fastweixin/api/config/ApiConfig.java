package com.github.sd4324530.fastweixin.api.config;

import com.github.sd4324530.fastweixin.api.response.GetJsApiTicketResponse;
import com.github.sd4324530.fastweixin.api.response.GetTokenResponse;
import com.github.sd4324530.fastweixin.exception.WeixinException;
import com.github.sd4324530.fastweixin.handle.ApiConfigChangeHandle;
import com.github.sd4324530.fastweixin.util.JSONUtil;
import com.github.sd4324530.fastweixin.util.NetWorkCenter;
import com.github.sd4324530.fastweixin.util.StrUtil;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Observable;

public abstract class ApiConfig extends Observable implements Serializable {
    private static final Logger log             = LoggerFactory.getLogger(ApiConfig.class);

    /**
     * 这里定义token正在刷新的标识，想要达到的目标是当有一个请求来获取token，发现token已经过期（我这里的过期逻辑是比官方提供的早100秒），然后开始刷新token
     * 在刷新的过程里，如果又继续来获取token，会先把旧的token返回，直到刷新结束，之后再来的请求，将获取到新的token
     * 利用AtomicBoolean实现原理：
     * 当请求来的时候，检查token是否已经过期（7100秒）以及标识是否已经是true（表示已经在刷新了，还没刷新完），过期则将此标识设为true，并开始刷新token
     * 在刷新结束前再次进来的请求，由于标识一直是true，而会直接拿到旧的token，由于我们的过期逻辑比官方的早100秒，所以旧的还可以继续用
     * 无论刷新token正在结束还是出现异常，都在最后将标识改回false，表示刷新工作已经结束
     */

    private final String  appid;
    private final String  secret;
    private       boolean enableJsApi;
    private final int leftTimeSecond=100;

    /**
     * 构造方法一，实现同时获取access_token。不启用jsApi
     *
     * @param appid  公众号appid
     * @param secret 公众号secret
     */
    public ApiConfig(String appid, String secret) {
        this(appid, secret, false);
    }

    /**
     * 构造方法二，实现同时获取access_token，启用jsApi
     *
     * @param appid       公众号appid
     * @param secret      公众号secret
     * @param enableJsApi 是否启动js api
     */
    public ApiConfig(String appid, String secret, boolean enableJsApi) {
        this.appid = appid;
        this.secret = secret;
        this.enableJsApi = enableJsApi;
    }

    protected void init(){
        initToken();
        if (enableJsApi) initJSToken();
    }

    public String getAppid() {
        return appid;
    }

    public String getSecret() {
        return secret;
    }

    protected abstract boolean needRefreshAccessToken();

    public String getAccessToken() {

        if (needRefreshAccessToken()) {
            initToken();
        }

        return getAccessTokenInner();
    }

    protected abstract String getAccessTokenInner();

    protected abstract void setAccessTokenInner(String accessToken,int expiresIn);

    protected abstract boolean needRefreshJsApiTicket();

    protected abstract String getJsApiTicketInner();

    protected abstract void setJsApiTicketInner(String jsApiTicket,int expiresIn);

    public String getJsApiTicket() {
        if (enableJsApi) {
            if (needRefreshJsApiTicket()) {
                initJSToken();
            }
        }
        return getJsApiTicketInner();
    }

    public boolean isEnableJsApi() {
        return enableJsApi;
    }


    /**
     * 添加配置变化监听器
     *
     * @param handle 监听器
     */
    public void addHandle(final ApiConfigChangeHandle handle) {
        super.addObserver(handle);
    }

    /**
     * 移除配置变化监听器
     *
     * @param handle 监听器
     */
    public void removeHandle(final ApiConfigChangeHandle handle) {
        super.deleteObserver(handle);
    }

    /**
     * 移除所有配置变化监听器
     */
    public void removeAllHandle() {
        super.deleteObservers();
    }

    /**
     * 初始化微信配置，即第一次获取access_token
     *
     */
    private void initToken() {
        log.debug("开始初始化access_token........");
        if (tokenLock()) {
            try {
                String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + this.appid + "&secret=" + this.secret;
                NetWorkCenter.get(url, null, new NetWorkCenter.ResponseCallback() {
                    @Override
                    public void onResponse(int resultCode, String resultJson) {
                        if (HttpStatus.SC_OK == resultCode) {
                            GetTokenResponse response = JSONUtil.toBean(resultJson, GetTokenResponse.class);
                            log.debug("获取access_token:{}", response.getAccessToken());
                            if (null == response.getAccessToken()) {
                                //刷新时间回滚
                                throw new WeixinException("微信公众号token获取出错，错误信息:" + response.getErrcode() + "," + response.getErrmsg());
                            }
                            setAccessTokenInner(response.getAccessToken(), response.getExpiresIn());
                            //设置通知点
                            setChanged();
                            notifyObservers(new ConfigChangeNotice(appid, ChangeType.ACCESS_TOKEN, response.getAccessToken()));
                        }
                    }
                });
            }finally {
                this.tokenUnlock();
            }
        }
    }

    /**
     * 初始化微信JS-SDK，获取JS-SDK token
     *
     */
    private void initJSToken() {
        log.debug("初始化 jsapi_ticket........");
        if (jsLock()) {
            try {
                String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=" + getAccessToken() + "&type=jsapi";
                NetWorkCenter.get(url, null, new NetWorkCenter.ResponseCallback() {
                    @Override
                    public void onResponse(int resultCode, String resultJson) {
                        if (HttpStatus.SC_OK == resultCode) {
                            GetJsApiTicketResponse response = JSONUtil.toBean(resultJson, GetJsApiTicketResponse.class);
                            log.debug("获取jsapi_ticket:{}", response.getTicket());
                            if (StrUtil.isBlank(response.getTicket())) {
                                //刷新时间回滚
                                throw new WeixinException("微信公众号jsToken获取出错，错误信息:" + response.getErrcode() + "," + response.getErrmsg());
                            }
                            setAccessTokenInner(response.getTicket(), response.getExpiresIn());
                            //设置通知点
                            setChanged();
                            notifyObservers(new ConfigChangeNotice(appid, ChangeType.JS_TOKEN, response.getTicket()));
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("刷新jsTicket出错.", e);
            }  finally {
                jsUnlock();
            }
        }
    }
    protected int getLeftTimeSecond(){
        return leftTimeSecond;
    }

    protected abstract boolean tokenLock();

    protected abstract  void tokenUnlock();

    protected abstract  boolean jsLock();

    protected abstract  void jsUnlock() ;
}
