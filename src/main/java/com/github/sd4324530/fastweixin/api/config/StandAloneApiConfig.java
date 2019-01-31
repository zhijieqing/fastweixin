package com.github.sd4324530.fastweixin.api.config;


import java.util.concurrent.atomic.AtomicBoolean;


public class StandAloneApiConfig extends ApiConfig {


    private final AtomicBoolean tokenRefreshing = new AtomicBoolean(false);
    private final AtomicBoolean jsRefreshing = new AtomicBoolean(false);

    private       String  accessToken;
    private       String  jsApiTicket;
    private       long    jsTokenExpireTime;
    private       long    weixinTokenExpireTime;
    /**
     * 构造方法一，实现同时获取access_token。不启用jsApi
     *
     * @param appid  公众号appid
     * @param secret 公众号secret
     */
    public StandAloneApiConfig(String appid, String secret) {
        this(appid, secret, false);
    }

    /**
     * 构造方法二，实现同时获取access_token，启用jsApi
     *
     * @param appid       公众号appid
     * @param secret      公众号secret
     * @param enableJsApi 是否启动js api
     */
    public StandAloneApiConfig(String appid, String secret, boolean enableJsApi) {
        super(appid, secret,enableJsApi);
        init();
    }

    @Override
    protected boolean needRefreshAccessToken() {
        return System.currentTimeMillis()>weixinTokenExpireTime;
    }


    @Override
    protected String getAccessTokenInner() {
        return this.accessToken;
    }
    @Override
    protected void setAccessTokenInner(String accessToken, int expiresIn) {
        this.accessToken=accessToken;
        this.weixinTokenExpireTime=System.currentTimeMillis()+(expiresIn-getLeftTimeSecond())*1000;
    }

    @Override
    protected boolean needRefreshJsApiTicket() {
        return System.currentTimeMillis()>jsTokenExpireTime;
    }

    @Override
    protected String getJsApiTicketInner() {
        return this.jsApiTicket;
    }
    @Override
    protected void setJsApiTicketInner(String jsApiTicket, int expiresIn) {
        this.jsApiTicket=jsApiTicket;
        this.jsTokenExpireTime=System.currentTimeMillis()+(expiresIn-getLeftTimeSecond())*1000;
    }


    protected boolean tokenLock() {
        return this.tokenRefreshing.compareAndSet(false, true);
    }

    protected void tokenUnlock() {
        this.jsRefreshing.set(false);
    }

    protected boolean jsLock() {
        return this.jsRefreshing.compareAndSet(false, true);
    }

    protected void jsUnlock() {
        this.jsRefreshing.set(false);
    }

}
