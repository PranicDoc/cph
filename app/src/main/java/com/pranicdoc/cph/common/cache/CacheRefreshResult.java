package com.pranicdoc.cph.common.cache;

import java.util.List;

/**
 * What a manual refresh covered. {@code key} is null when whole caches were refreshed;
 * {@code redisCleared} is false when Redis could not be reached for at least one cache.
 */
public record CacheRefreshResult(List<String> caches, String key, boolean redisCleared) {
}
