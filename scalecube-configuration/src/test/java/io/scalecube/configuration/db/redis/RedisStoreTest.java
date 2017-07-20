package io.scalecube.configuration.db.redis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.redisson.Redisson;

public class RedisStoreTest {
  
  private final RedisStore<String> store = new RedisStore<>(Redisson.create());
  
  @Test
  public void test_get(){
    store.put("test_get", "key1","1");
    String value = store.get("test_get", "key1");
    assertEquals(value,"1");
  }
}
