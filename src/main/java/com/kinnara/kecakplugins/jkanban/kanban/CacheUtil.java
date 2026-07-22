package com.kinnara.kecakplugins.jkanban.kanban;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

import javax.annotation.Nullable;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class CacheUtil {
    public static String getCacheKey(Class<?> caller, String... args) {
        return String.join("_", caller.getName(), String.join("_", args));
    }

    public static String getSessionId() {
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        return Optional.ofNullable(request)
                .map(HttpServletRequest::getCookies)
                .stream()
                .flatMap(Arrays::stream)
                .filter(c -> "JSESSIONID".equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse("");
    }

    public static Object getCached(String key) {
        final Cache cache = (Cache) AppUtil.getApplicationContext().getBean("externalDataCache");
        return Optional.ofNullable(cache)
                .map(c -> c.get(key))
                .map(Element::getObjectValue)
                .orElse(null);
    }

    public static <T> T putCache(String key, T value) {
        final Cache cache = (Cache) AppUtil.getApplicationContext().getBean("externalDataCache");
        if(cache != null && value != null) cache.put(new Element(key, value));
        return value;
    }

    @Nullable
    public static <T> T getFromCache(String key, Supplier<T> whenMissed) {
        assert Objects.nonNull(key);
        assert Objects.nonNull(whenMissed);

        T cached = (T) getCached(key);
        if (cached != null) {

            LogUtil.debug(CacheUtil.class.getName(), "Cache hit for key [" + key + "] value [" + cached + "]");

            return cached;
        }

        LogUtil.debug(CacheUtil.class.getName(), "Cache missed for key [" + key + "]");

        T value = whenMissed.get();

        return putCache(key, value);
    }
}
