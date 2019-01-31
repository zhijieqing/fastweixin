package com.github.sd4324530.fastweixin.api.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class RedisApiConfig extends ApiConfig {

    private Lock tokenLock;
    private Lock jsLock;
    private StringRedisTemplate redisTemplate;
    private volatile String  accessToken;
    private volatile String  jsApiTicket;
    private String accessTokenKey;
    private String accessTokenLockKey;
    private String jsApiTicketKey;
    private String jsApiTicketLockKey;

    /**
     * 构造方法一，实现同时获取access_token。不启用jsApi
     *
     * @param appid  公众号appid
     * @param secret 公众号secret
     */
    public RedisApiConfig(String appid, String secret,StringRedisTemplate redisTemplate,Lock tokenLock) {
        this(appid, secret, false,redisTemplate,tokenLock,null);
    }

    /**
     * 构造方法二，实现同时获取access_token，启用jsApi
     *
     * @param appid       公众号appid
     * @param secret      公众号secret
     * @param enableJsApi 是否启动js api
     */
    public RedisApiConfig(String appid, String secret, boolean enableJsApi,StringRedisTemplate redisTemplate,Lock tokenLock,Lock jsLock) {
        super(appid, secret,enableJsApi);
        this.tokenLock=tokenLock;
        this.jsLock=jsLock;
        this.redisTemplate=redisTemplate;
        this.accessTokenKey="accessToken_"+appid;
        this.accessTokenLockKey="accessTokenLock_"+appid;
        this.jsApiTicketKey="jsApiTicket_"+appid;
        this.jsApiTicketLockKey="jsApiTicketLock_"+appid;
    }

    /**
     * 判断是否需要刷新accessToken
     * key不存在或者key过期时间小于安全时间,则需要刷新
     * @return
     */
    @Override
    protected boolean needRefreshAccessToken() {
        return needRefresh(0,accessTokenKey,getLeftTimeSecond());
    }

    /**
     * 判断是否需要刷新accessToken
     * key不存在或者key过期时间小于安全时间,则需要刷新
     * @param keyType 0=accessToken，其他值为jsTicket
     * @return
     */
    private boolean needRefresh(int keyType, String key, int safeLeftTime) {

        String value = redisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(value)) {
            return true;
        } else {
            long leftTime = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (leftTime < safeLeftTime) {
                /*key过期时间小于安全时间，则需要刷新token*/
                return true;
            }
            if (keyType == 0) {
                this.accessToken = value;
            } else {
                this.jsApiTicket = value;
            }
        }
        return false;
    }

    @Override
    protected String getAccessTokenInner() {
        return this.accessToken;
    }

    @Override
    protected void setAccessTokenInner(String accessToken, int expiresIn) {
        redisTemplate.opsForValue().set(accessTokenKey,accessToken,expiresIn,TimeUnit.SECONDS);
    }

    @Override
    protected boolean needRefreshJsApiTicket() {
        return needRefresh(1,jsApiTicketKey,getLeftTimeSecond());
    }

    @Override
    protected String getJsApiTicketInner() {
        return this.jsApiTicket;
    }

    @Override
    protected void setJsApiTicketInner(String jsApiTicket, int expiresIn) {
        redisTemplate.opsForValue().set(jsApiTicketKey,jsApiTicket,expiresIn,TimeUnit.SECONDS);
    }

    public String getAccessTokenLockKey() {
        return accessTokenLockKey;
    }

    public String getJsApiTicketLockKey() {
        return jsApiTicketLockKey;
    }

    protected boolean tokenLock() {
        return tokenLock.tryLock();
    }

    protected void tokenUnlock() {
        tokenLock.unlock();
    }

    protected boolean jsLock() {
        return jsLock.tryLock();
    }

    protected void jsUnlock() {
        jsLock.unlock();
    }
}
