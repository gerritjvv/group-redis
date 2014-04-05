package group_redis;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.Test;

public class TestConn {

	
	@Test
	public void testJoin(){
		Object connector = RedisConn.create_group_connector("localhost");
		
		RedisConn.join(connector);
		RedisConn.join(connector, "anotherhost");
		
		Collection<Map<?,?>> members = RedisConn.getMembers(connector);
		System.out.println(Arrays.toString(members.toArray()));
		
		assertEquals(members.size(), 2);
		

		RedisConn.close(connector);
	}
	
	@Test
	public void testLocks(){
		Object connector = RedisConn.create_group_connector("localhost");
		
		assertTrue(RedisConn.lock(connector, "lock1"));
		assertFalse(RedisConn.lock(connector, "lock1"));
		
		assertFalse(RedisConn.release(connector, "another-member", "lock1"));
		

		assertTrue(RedisConn.release(connector, "lock1"));
		assertFalse(RedisConn.release(connector, "lock1"));
	
		assertTrue(RedisConn.reentrant_lock(connector, "lock2"));
		assertTrue(RedisConn.reentrant_lock(connector, "lock2"));
		
		assertFalse(RedisConn.reentrant_lock(connector, "another-member", "lock2"));
		
		assertFalse(RedisConn.release(connector, "another-member", "lock2"));
		
		assertTrue(RedisConn.release(connector, "lock2"));
	
		RedisConn.close(connector);
	}
	
	@Test
	public void testEmhperalSets(){
		Object connector = RedisConn.create_group_connector("localhost");
		
		RedisConn.empheral_set(connector, "mykey1", 1);
		assertEquals(RedisConn.empheral_get(connector, "mykey1"), 1);
		
		RedisConn.close(connector);
	}
	
	@Test
	public void testPersistentSets(){
		Object connector = RedisConn.create_group_connector("localhost");
		
		RedisConn.persistent_set(connector, "mykey2", 1);
		assertEquals(RedisConn.persistent_get(connector, "mykey2"), 1);
		
		RedisConn.close(connector);
		
	}
	
}
