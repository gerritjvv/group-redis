package group_redis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class RedisConn {

	static {
		RT.var("clojure.core", "require").invoke(Symbol.create("group-redis.core"));
	}

	
	@SuppressWarnings("unchecked")
	public static final Collection<Map<?, ?>> getMembers(Object connector){
		return (Collection<Map<?, ?>>) RT.var("group-redis.core", "get-members").invoke(connector);
	}
	
	public static final void addSubGroup(Object connector, String subGroup){
		RT.var("group-redis.core", "add-sub-group").invoke(connector, subGroup);
	}
	
	public static final void removeSubGroup(Object connector, String subGroup){
		RT.var("group-redis.core", "remove-sub-group").invoke(connector, subGroup);
	}
	
	public static final void join(Object connector){
		RT.var("group-redis.core", "join").invoke(connector);
	}
	
	public static final void join(Object connector, String host){
		RT.var("group-redis.core", "join").invoke(connector, host);
	}
	
	public static final boolean lock(Object connector, String key){
		return (Boolean)RT.var("group-redis.core", "lock").invoke(connector, key);
	}
	
	public static final boolean lock(Object connector, String host, String key){
		return (Boolean)RT.var("group-redis.core", "lock").invoke(connector, host, key);
	}
	
	public static final boolean release(Object connector, String key){
		return (Boolean)RT.var("group-redis.core", "release").invoke(connector, key);
	}

	public static final boolean release(Object connector, String host, String key){
		return (Boolean)RT.var("group-redis.core", "release").invoke(connector, host, key);
	}

	public static final boolean reentrant_lock (Object connector, String key){
		return (Boolean)RT.var("group-redis.core", "reentrant-lock").invoke(connector, key);
	}

	public static final boolean reentrant_lock (Object connector, String host, String key){
		return (Boolean)RT.var("group-redis.core", "reentrant-lock").invoke(connector, host, key);
	}

	
	public static final Object create_group_connector(String host){
		return RT.var("group-redis.core", "create-group-connector").invoke(host);
	}
	
	/**
	 * Open a connector to redis.
	 * @param host
	 * @param props keys: group-name heart-beat-freq sub-groups sub-groups. Defaults {group-name "default-group" heart-beat-freq 5 sub-groups ["default"]}
	 * @return
	 */
	public static final Object create_group_connector(String host, Map<String, Object> props){
		
		if(!props.containsKey("heart-beat-freq"))
			props.put("heart-beat-freq", 5);
		
		if(!props.containsKey("group-name"))
			props.put("group-name", "default-group");
		
		if(!props.containsKey("sub-groups"))
			props.put("sub-group", PersistentVector.create("default"));
		
		return RT.var("group-redis.core", "create-group-connector").invoke(host, toMap(props));
	}
	
	/**
	 * Close connector
	 * @param connector
	 */
	public static final void close(Object connector){
		RT.var("group-redis.core", "close").invoke(connector);
	}
	
	
	public static final void empheral_set(Object connector, String key, Object val){
		RT.var("group-redis.core", "empheral-set").invoke(connector, key, val);
	}
	
	public static final Object empheral_get(Object connector, String key){
		return RT.var("group-redis.core", "empheral-get").invoke(connector, key);
	}
	
	public static final void persistent_set(Object connector, String key, Object val){
		RT.var("group-redis.core", "persistent-set").invoke(connector, key, val);
	}
	
	public static final Object persistent_get(Object connector, String key){
		return RT.var("group-redis.core", "persistent-get").invoke(connector, key);
	}
	
	private static final PersistentArrayMap toMap(Map<String, Object> map){
		List<Object> propvals = new ArrayList<Object>();

		for (Map.Entry<String, Object> entry : map.entrySet()) {
			propvals.add(entry.getKey());
			propvals.add(entry.getValue());
		}
		
		return PersistentArrayMap.createAsIfByAssoc(propvals.toArray());
	}
	
}
